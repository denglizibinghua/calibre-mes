package com.bikeassembly.review;

import com.bikeassembly.AbstractPostgresContainerIntegrationTest;
import com.bikeassembly.dto.WxBindDTO;
import com.bikeassembly.dto.WxLoginDTO;
import com.bikeassembly.entity.SysUser;
import com.bikeassembly.mapper.SysUserMapper;
import com.bikeassembly.service.SysUserService;
import com.bikeassembly.service.WechatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/** 在容器化 PostgreSQL 上执行实际Mapper绑定；测试事务结束全部回滚，不留下测试账号。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class WechatBindingDatabaseTest extends AbstractPostgresContainerIntegrationTest {
    @Autowired private SysUserService service;
    @Autowired private SysUserMapper users;
    @MockBean private WechatService wechat;

    @Test
    void transfersUniqueOpenidAndKeepsExistingRoleInRealDatabase() {
        String suffix=UUID.randomUUID().toString().replace("-","");
        String openid="binding_verify_"+suffix;
        String username="binding_verify_"+suffix;
        SysUser target=new SysUser();
        target.setUsername(username);
        target.setNickname("绑定回归测试账号");
        target.setPassword(new BCryptPasswordEncoder().encode("Binding@Test123"));
        target.setRoleId(4L);
        target.setStatus("0");
        target.setMustChangePassword(0);
        users.insert(target);
        when(wechat.code2Session("binding-test-code")).thenReturn(openid);
        WxLoginDTO login=new WxLoginDTO();
        login.setCode("binding-test-code");
        var pending=service.wxLogin(login);
        assertTrue(pending.getNeedBind());
        assertNull(pending.getAccessToken());
        assertNull(users.selectById(pending.getUserId()).getRoleId());
        WxBindDTO bind=new WxBindDTO();
        bind.setBindType("existing");
        bind.setUsername(username);
        bind.setPassword("Binding@Test123");
        var response=service.bindUser(pending.getUserId(),bind);
        assertEquals(target.getUserId(),response.getUserId());
        assertEquals(4L,response.getRoleId());
        assertEquals("绑定回归测试账号",response.getNickname());
        assertNull(users.selectById(pending.getUserId()));
        assertEquals(target.getUserId(),users.findByOpenid(openid).getUserId());
        assertEquals(4L,users.selectById(target.getUserId()).getRoleId());
    }
}
