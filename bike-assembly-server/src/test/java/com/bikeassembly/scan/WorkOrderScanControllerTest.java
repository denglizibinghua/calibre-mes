package com.bikeassembly.scan;

import com.bikeassembly.controller.app.AppScanController;
import com.bikeassembly.entity.SysUser;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.exception.ForbiddenException;
import com.bikeassembly.exception.GlobalExceptionHandler;
import com.bikeassembly.interceptor.JwtTokenAppInterceptor;
import com.bikeassembly.mapper.SysUserMapper;
import com.bikeassembly.service.PermissionService;
import com.bikeassembly.service.WorkOrderScanService;
import com.bikeassembly.utils.JwtUtil;
import com.bikeassembly.vo.ScanResolveVO;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.util.Set;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WorkOrderScanControllerTest {
    MockMvc mvc;
    WorkOrderScanService service;
    JwtUtil jwt;
    String token;
    @BeforeEach void setup() {
        service = mock(WorkOrderScanService.class);
        PermissionService permissions = mock(PermissionService.class);
        SysUserMapper users = mock(SysUserMapper.class);
        jwt = new JwtUtil();
        ReflectionTestUtils.setField(jwt, "appSecretKey", "scan-unit-test-app-key-12345678901234567890123456789");
        ReflectionTestUtils.setField(jwt, "appTokenName", "token");
        ReflectionTestUtils.setField(jwt, "appAccessTtl", 60000L);
        SysUser user = new SysUser();
        user.setUserId(10L);
        user.setStatus("0");
        when(users.selectById(10L)).thenReturn(user);
        when(permissions.getUserPermissions(10L)).thenReturn(Set.of("app:inspection:view"));
        when(permissions.getUserDataScope(10L)).thenReturn("SELF");
        token = jwt.createAppAccessToken(10L, "test-inspector");
        mvc = MockMvcBuilders.standaloneSetup(new AppScanController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new JwtTokenAppInterceptor(jwt, permissions, users)).build();
    }
    @AfterEach void cleanup() { com.bikeassembly.context.BaseContext.removeAll(); }

    @Test void realSignedAppTokenCanReachInspectionOnlyAction() throws Exception {
        ScanResolveVO vo = new ScanResolveVO();
        vo.setBusinessType("WORK_ORDER"); vo.setWorkOrderId(42L); vo.setWorkOrderNo("MO-42");
        vo.setActions(List.of("INSPECTION_PENDING")); vo.setPendingInspectionCount(1L);
        when(service.resolve("BIKE-MES:1:WORK_ORDER:42")).thenReturn(vo);
        mvc.perform(post("/app/scan/resolve").header("token", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"BIKE-MES:1:WORK_ORDER:42\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.actions[0]").value("INSPECTION_PENDING"));
    }
    @Test void missingTokenIs401() throws Exception {
        mvc.perform(post("/app/scan/resolve").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"MO-42\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void temporaryBindingTokenCannotScan() throws Exception {
        mvc.perform(post("/app/scan/resolve").header("token", jwt.createTempToken(10L))
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"MO-42\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void tamperedSignatureIs401() throws Exception {
        mvc.perform(post("/app/scan/resolve").header("token", token.substring(0, token.lastIndexOf('.') + 1) + "invalid-signature")
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"MO-42\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void blankInputReturnsReadableValidationError() throws Exception {
        mvc.perform(post("/app/scan/resolve").header("token", token).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"\"}"))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.msg").value("content：扫码内容不能为空"));
        verifyNoInteractions(service);
    }
    @Test void deniedOwnershipReturns403AndNoTaskData() throws Exception {
        when(service.resolve("MO-42")).thenThrow(new ForbiddenException("您无权访问该工单的任务"));
        mvc.perform(post("/app/scan/resolve").header("token", token).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"MO-42\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("您无权访问该工单的任务"));
    }
    @Test void nonexistentOrderPreservesBusinessCodeAndMessage() throws Exception {
        when(service.resolve("MO-MISSING")).thenThrow(new BaseException(46003, "工单不存在或已删除"));
        mvc.perform(post("/app/scan/resolve").header("token", token).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"MO-MISSING\"}"))
                .andExpect(jsonPath("$.code").value(46003)).andExpect(jsonPath("$.msg").value("工单不存在或已删除"));
    }
}
