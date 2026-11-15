package com.bikeassembly.review;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.bikeassembly.dto.WxBindDTO;
import com.bikeassembly.dto.WxLoginDTO;
import com.bikeassembly.entity.SysRole;
import com.bikeassembly.entity.SysUser;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.mapper.SysRoleMapper;
import com.bikeassembly.mapper.SysUserMapper;
import com.bikeassembly.mapper.SysUserPasswordHistoryMapper;
import com.bikeassembly.service.PermissionService;
import com.bikeassembly.service.RefreshTokenService;
import com.bikeassembly.service.WechatService;
import com.bikeassembly.service.impl.LoginFailService;
import com.bikeassembly.service.impl.SysUserServiceImpl;
import com.bikeassembly.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WechatBindingFlowTest {
    private SysUserMapper users;
    private SysRoleMapper roles;
    private JwtUtil jwt;
    private WechatService wechat;
    private LoginFailService failures;
    private PermissionService permissions;
    private RefreshTokenService refresh;
    private SysUserPasswordHistoryMapper history;
    private SysUserServiceImpl service;
    @BeforeEach void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "binding-test"), SysUser.class);
        users=mock(SysUserMapper.class); roles=mock(SysRoleMapper.class); jwt=mock(JwtUtil.class);
        wechat=mock(WechatService.class); failures=mock(LoginFailService.class);
        permissions=mock(PermissionService.class); refresh=mock(RefreshTokenService.class);
        history=mock(SysUserPasswordHistoryMapper.class); service=new SysUserServiceImpl();
        ReflectionTestUtils.setField(service,"sysUserMapper",users);
        ReflectionTestUtils.setField(service,"sysRoleMapper",roles);
        ReflectionTestUtils.setField(service,"jwtUtil",jwt);
        ReflectionTestUtils.setField(service,"wechatService",wechat);
        ReflectionTestUtils.setField(service,"loginFailService",failures);
        ReflectionTestUtils.setField(service,"permissionService",permissions);
        ReflectionTestUtils.setField(service,"refreshTokenService",refresh);
        ReflectionTestUtils.setField(service,"passwordHistoryMapper",history);
    }
    private WxLoginDTO loginRequest() {
        WxLoginDTO dto=new WxLoginDTO(); dto.setCode("test-only-code");
        when(wechat.code2Session("test-only-code")).thenReturn("test-only-openid"); return dto;
    }
    private SysUser pending() {
        SysUser user=new SysUser(); user.setUserId(80L);user.setUsername("wx_00000000000000000000000000000000");user.setNickname("待绑定微信");
        user.setOpenid("test-only-openid");user.setStatus("0");
        when(users.selectById(80L)).thenReturn(user);return user;
    }
    private SysUser target() {
        SysUser user=new SysUser();user.setUserId(4L);user.setUsername("inspector01");
        user.setNickname("质检员");user.setRoleId(4L);user.setStatus("0");
        user.setPassword(new BCryptPasswordEncoder().encode("test-password"));
        when(users.getByUsername("inspector01")).thenReturn(user);return user;
    }
    private void validRole() {
        SysRole role=new SysRole();role.setRoleId(4L);role.setStatus("0");
        when(roles.selectById(4L)).thenReturn(role);
    }
    private WxBindDTO bindRequest() {
        WxBindDTO dto=new WxBindDTO();dto.setBindType("existing");dto.setUsername(" inspector01 ");
        dto.setPassword("test-password");return dto;
    }
    private void tokens() {
        when(jwt.createAppAccessToken(4L,"inspector01")).thenReturn("test-access");
        when(jwt.createAppRefreshToken(4L,"inspector01")).thenReturn("test-refresh");
        when(jwt.getAppAccessTtl()).thenReturn(1800000L);when(jwt.getAppRefreshTtl()).thenReturn(604800000L);
        when(permissions.getUserPermissions(4L)).thenReturn(Set.of("app:inspection:view"));
        when(permissions.getUserDataScope(4L)).thenReturn("SELF");
    }
    @Test void newWechatGetsOnlyTempTokenAndNoRole() {
        WxLoginDTO dto=loginRequest();
        doAnswer(call->{((SysUser)call.getArgument(0)).setUserId(80L);return 1;}).when(users).insert(any(SysUser.class));
        when(jwt.createTempToken(80L)).thenReturn("test-temp");
        var response=service.wxLogin(dto);
        ArgumentCaptor<SysUser> created=ArgumentCaptor.forClass(SysUser.class);
        verify(users).insert(created.capture());assertNull(created.getValue().getRoleId());
        assertEquals(3,response.getStatus());assertTrue(response.getNeedBind());
        assertEquals("test-temp",response.getTempToken());assertNull(response.getAccessToken());assertNull(response.getRefreshToken());
        verify(jwt,never()).createAppAccessToken(anyLong(),anyString());verifyNoInteractions(roles,permissions,refresh);
    }
    @Test void pendingWechatReturnsBindingWithoutCreatingAccount() {
        WxLoginDTO dto=loginRequest();SysUser user=new SysUser();user.setUserId(80L);user.setUsername("wx_00000000000000000000000000000000");user.setNickname("待绑定微信");user.setStatus("0");
        when(users.findByOpenid("test-only-openid")).thenReturn(user);
        when(jwt.createTempToken(80L)).thenReturn("test-temp");
        assertTrue(service.wxLogin(dto).getNeedBind());verify(users,never()).insert(any(SysUser.class));
        verifyNoInteractions(refresh,roles,permissions);
    }
    @Test void boundInspectorKeepsDatabaseRoleAndPermissions() {
        WxLoginDTO dto=loginRequest();SysUser user=target();when(users.findByOpenid("test-only-openid")).thenReturn(user);
        validRole();tokens();var response=service.wxLogin(dto);
        assertEquals(4L,response.getRoleId());assertEquals(Set.of("app:inspection:view"),Set.copyOf(response.getUserInfo().getPermissions()));
        assertFalse(response.getNeedBind());verify(jwt,never()).createTempToken(anyLong());
    }
    @Test void formalWxPrefixAccountKeepsRoleAndGetsFullSession() {
        WxLoginDTO dto=loginRequest();SysUser user=target();user.setUsername("wx_inspector");
        when(users.findByOpenid("test-only-openid")).thenReturn(user);
        validRole();tokens();
        when(jwt.createAppAccessToken(4L,"wx_inspector")).thenReturn("test-access");
        when(jwt.createAppRefreshToken(4L,"wx_inspector")).thenReturn("test-refresh");
        var response=service.wxLogin(dto);
        assertEquals(1,response.getStatus());assertEquals(4L,response.getRoleId());
        assertEquals("test-access",response.getAccessToken());
        verify(jwt,never()).createTempToken(anyLong());
    }
    @Test void missingSystemRoleDoesNotBecomeOperator() {
        WxLoginDTO dto=loginRequest();SysUser user=target();user.setRoleId(null);
        when(users.findByOpenid("test-only-openid")).thenReturn(user);
        assertThrows(BaseException.class,()->service.wxLogin(dto));assertNull(user.getRoleId());
        verifyNoInteractions(refresh);verify(users,never()).updateById(any(SysUser.class));
    }
    @Test void cannotChooseRoleThroughRegistration() {
        WxBindDTO dto=new WxBindDTO();dto.setBindType("new");dto.setRoleId(1L);
        assertThrows(BaseException.class,()->service.bindUser(80L,dto));verifyNoInteractions(users,roles,refresh);
    }
    @Test void normalAccountCannotBeBindingPlaceholder() {
        SysUser normal=new SysUser();normal.setUsername("operator01");normal.setRoleId(3L);
        when(users.selectById(80L)).thenReturn(normal);
        assertThrows(BaseException.class,()->service.bindUser(80L,bindRequest()));verify(users,never()).deleteById(anyLong());
    }
    @Test void wrongPasswordPreservesBindingAndPlaceholder() {
        pending();target();WxBindDTO dto=bindRequest();dto.setPassword("wrong-password");
        assertThrows(BaseException.class,()->service.bindUser(80L,dto));
        verify(failures).incrementFailCount("inspector01");verify(users,never()).update(isNull(),any(Wrapper.class));
        verify(users,never()).deleteById(anyLong());
    }
    @Test void bindingReleasesUniqueOpenidFirstAndKeepsIdentity() {
        pending();SysUser user=target();validRole();tokens();
        when(users.update(isNull(),any(Wrapper.class))).thenReturn(1);when(users.deleteById(80L)).thenReturn(1);
        var response=service.bindUser(80L,bindRequest());
        assertEquals(4L,response.getUserId());assertEquals(4L,response.getRoleId());
        assertEquals("inspector01",response.getUsername());assertEquals("质检员",response.getNickname());
        assertEquals(4L,user.getRoleId());assertEquals("质检员",user.getNickname());
        var order=inOrder(users);order.verify(users).selectById(80L);order.verify(users).getByUsername("inspector01");
        ArgumentCaptor<Wrapper> changes=ArgumentCaptor.forClass(Wrapper.class);
        order.verify(users,times(2)).update(isNull(),changes.capture());
        var released=(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SysUser>)changes.getAllValues().get(0);
        var assigned=(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SysUser>)changes.getAllValues().get(1);
        assertTrue(released.getParamNameValuePairs().containsValue(null));
        assertTrue(assigned.getParamNameValuePairs().containsValue("test-only-openid"));
        order.verify(users).deleteById(80L);verify(refresh).remove(any(Wrapper.class));verify(history).delete(any(Wrapper.class));
    }
    @Test void concurrentBindingChangeDoesNotDeletePlaceholder() {
        pending();target();validRole();when(users.update(isNull(),any(Wrapper.class))).thenReturn(1,0);
        assertThrows(BaseException.class,()->service.bindUser(80L,bindRequest()));
        verify(users,never()).deleteById(anyLong());verifyNoInteractions(refresh);
    }
}
