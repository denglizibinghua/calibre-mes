package com.bikeassembly.ai;

import com.bikeassembly.dto.FaceLoginDTO;
import com.bikeassembly.dto.FaceTemplateDTO;
import com.bikeassembly.entity.SysUser;
import com.bikeassembly.entity.UserFaceTemplate;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.mapper.SysUserMapper;
import com.bikeassembly.mapper.UserFaceTemplateMapper;
import com.bikeassembly.service.FaceAuthService;
import com.bikeassembly.service.FaceEmbeddingClient;
import com.bikeassembly.service.SysUserService;
import com.bikeassembly.service.impl.LoginFailService;
import com.bikeassembly.utils.JwtUtil;
import com.bikeassembly.vo.UserLoginVO;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.argThat;

class FaceAuthServiceTest {
    private static final String ENCRYPTION_KEY = "0123456789abcdef0123456789abcdef";

    @Test
    void disabledAccountsDoNotCreateFalseOneToManyAmbiguity() throws Exception {
        FaceEmbeddingClient embeddingClient = mock(FaceEmbeddingClient.class);
        UserFaceTemplateMapper templateMapper = mock(UserFaceTemplateMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUserService userService = mock(SysUserService.class);
        LoginFailService loginFailService = mock(LoginFailService.class);
        double[] embedding = new double[128];
        embedding[0] = 1;
        when(embeddingClient.extract("frame")).thenReturn(
                new FaceEmbeddingClient.FaceResult(embedding, true));

        UserFaceTemplate activeTemplate = encryptedTemplate(1L, embedding);
        UserFaceTemplate disabledTemplate = encryptedTemplate(2L, embedding);
        when(templateMapper.selectList(isNull())).thenReturn(List.of(activeTemplate, disabledTemplate));
        when(userMapper.selectById(1L)).thenReturn(user(1L, "0"));
        when(userMapper.selectById(2L)).thenReturn(user(2L, "1"));
        UserLoginVO expected = UserLoginVO.builder().id(1L).build();
        when(userService.faceLogin(1L, JwtUtil.CLIENT_TYPE_ADMIN)).thenReturn(expected);
        FaceAuthService service = new FaceAuthService(embeddingClient, templateMapper, userMapper,
                userService, loginFailService, ENCRYPTION_KEY, 0.42, 0.06);

        FaceLoginDTO request = new FaceLoginDTO();
        request.setImageBase64("frame");

        assertSame(expected, service.login(request, JwtUtil.CLIENT_TYPE_ADMIN));
        verify(userService).faceLogin(1L, JwtUtil.CLIENT_TYPE_ADMIN);
    }

    @Test
    void registrationRequiresExplicitConsent() {
        FaceEmbeddingClient embeddingClient = mock(FaceEmbeddingClient.class);
        FaceAuthService service = new FaceAuthService(embeddingClient,
                mock(UserFaceTemplateMapper.class), mock(SysUserMapper.class), mock(SysUserService.class),
                mock(LoginFailService.class), ENCRYPTION_KEY, 0.42, 0.06);
        FaceTemplateDTO request = new FaceTemplateDTO();
        request.setImageBase64("frame");
        request.setConsent(false);

        assertThrows(BaseException.class, () -> service.register(1L, request));
        verifyNoInteractions(embeddingClient);
    }

    @Test
    void registrationPersistsOnlyEncryptedTemplateBytes() throws Exception {
        FaceEmbeddingClient embeddingClient = mock(FaceEmbeddingClient.class);
        UserFaceTemplateMapper templateMapper = mock(UserFaceTemplateMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        double[] embedding = new double[128];
        embedding[0] = 1;
        ByteBuffer plainBuffer = ByteBuffer.allocate(Integer.BYTES + embedding.length * Double.BYTES);
        plainBuffer.putInt(embedding.length);
        for (double value : embedding) plainBuffer.putDouble(value);
        byte[] plainTemplate = plainBuffer.array();
        when(userMapper.selectById(3L)).thenReturn(user(3L, "0"));
        when(embeddingClient.extract("frame")).thenReturn(
                new FaceEmbeddingClient.FaceResult(embedding, true));
        FaceAuthService service = new FaceAuthService(embeddingClient, templateMapper, userMapper,
                mock(SysUserService.class), mock(LoginFailService.class),
                ENCRYPTION_KEY, 0.42, 0.06);
        FaceTemplateDTO request = new FaceTemplateDTO();
        request.setImageBase64("frame");
        request.setConsent(true);

        service.register(3L, request);

        verify(templateMapper).upsertTemplate(argThat(template ->
                template.getUserId().equals(3L)
                        && template.getConsentedAt() != null
                        && template.getEmbeddingCiphertext().length > 128
                        && !java.util.Arrays.equals(template.getEmbeddingCiphertext(), plainTemplate)));
    }

    @Test
    void shortEncryptionKeysDisableTemplateRegistration() {
        FaceEmbeddingClient embeddingClient = mock(FaceEmbeddingClient.class);
        FaceAuthService service = new FaceAuthService(embeddingClient,
                mock(UserFaceTemplateMapper.class), mock(SysUserMapper.class), mock(SysUserService.class),
                mock(LoginFailService.class), "short", 0.42, 0.06);
        FaceTemplateDTO request = new FaceTemplateDTO();
        request.setImageBase64("frame");
        request.setConsent(true);

        assertThrows(BaseException.class, () -> service.register(1L, request));
        verifyNoInteractions(embeddingClient);
    }

    @Test
    void lockedAccountCannotBypassLoginLockWithFaceAuthentication() throws Exception {
        FaceEmbeddingClient embeddingClient = mock(FaceEmbeddingClient.class);
        UserFaceTemplateMapper templateMapper = mock(UserFaceTemplateMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUserService userService = mock(SysUserService.class);
        LoginFailService loginFailService = mock(LoginFailService.class);
        double[] embedding = new double[128];
        embedding[0] = 1;
        when(embeddingClient.extract("frame")).thenReturn(
                new FaceEmbeddingClient.FaceResult(embedding, true));
        when(userMapper.findByUsername("alice")).thenReturn(user(1L, "alice", "0"));
        when(templateMapper.selectById(1L)).thenReturn(encryptedTemplate(1L, embedding));
        when(loginFailService.isLocked("alice")).thenReturn(true);
        FaceAuthService service = new FaceAuthService(embeddingClient, templateMapper, userMapper,
                userService, loginFailService, ENCRYPTION_KEY, 0.42, 0.06);
        FaceLoginDTO request = new FaceLoginDTO();
        request.setUsername("alice");
        request.setImageBase64("frame");

        assertThrows(BaseException.class, () -> service.login(request, JwtUtil.CLIENT_TYPE_ADMIN));
        verify(userService, never()).faceLogin(any(), any());
    }

    @Test
    void oneToOneMismatchCannotSignIn() throws Exception {
        FaceEmbeddingClient embeddingClient = mock(FaceEmbeddingClient.class);
        UserFaceTemplateMapper templateMapper = mock(UserFaceTemplateMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUserService userService = mock(SysUserService.class);
        double[] stored = new double[128];
        double[] probe = new double[128];
        stored[0] = 1;
        probe[1] = 1;
        when(userMapper.findByUsername("alice")).thenReturn(user(1L, "alice", "0"));
        when(templateMapper.selectById(1L)).thenReturn(encryptedTemplate(1L, stored));
        when(embeddingClient.extract("frame")).thenReturn(
                new FaceEmbeddingClient.FaceResult(probe, true));
        FaceAuthService service = new FaceAuthService(embeddingClient, templateMapper, userMapper,
                userService, mock(LoginFailService.class), ENCRYPTION_KEY, 0.42, 0.06);
        FaceLoginDTO request = new FaceLoginDTO();
        request.setUsername("alice");
        request.setImageBase64("frame");

        assertThrows(BaseException.class, () -> service.login(request, JwtUtil.CLIENT_TYPE_ADMIN));
        verify(userService, never()).faceLogin(any(), any());
    }

    @Test
    void oneToManyAmbiguousMatchRequiresAccountVerification() throws Exception {
        FaceEmbeddingClient embeddingClient = mock(FaceEmbeddingClient.class);
        UserFaceTemplateMapper templateMapper = mock(UserFaceTemplateMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUserService userService = mock(SysUserService.class);
        double[] embedding = new double[128];
        embedding[0] = 1;
        when(embeddingClient.extract("frame")).thenReturn(
                new FaceEmbeddingClient.FaceResult(embedding, true));
        when(templateMapper.selectList(isNull())).thenReturn(List.of(
                encryptedTemplate(1L, embedding), encryptedTemplate(2L, embedding)));
        when(userMapper.selectById(1L)).thenReturn(user(1L, "alice", "0"));
        when(userMapper.selectById(2L)).thenReturn(user(2L, "bob", "0"));
        FaceAuthService service = new FaceAuthService(embeddingClient, templateMapper, userMapper,
                userService, mock(LoginFailService.class), ENCRYPTION_KEY, 0.42, 0.06);
        FaceLoginDTO request = new FaceLoginDTO();
        request.setImageBase64("frame");

        assertThrows(BaseException.class, () -> service.login(request, JwtUtil.CLIENT_TYPE_APP));
        verify(userService, never()).faceLogin(any(), any());
    }

    @Test
    void revokingRemovesTheStoredTemplate() {
        UserFaceTemplateMapper templateMapper = mock(UserFaceTemplateMapper.class);
        FaceAuthService service = new FaceAuthService(mock(FaceEmbeddingClient.class),
                templateMapper, mock(SysUserMapper.class), mock(SysUserService.class),
                mock(LoginFailService.class), ENCRYPTION_KEY, 0.42, 0.06);

        service.revoke(7L);

        verify(templateMapper).deleteById(7L);
    }

    private static SysUser user(Long id, String status) {
        return user(id, "user" + id, status);
    }

    private static SysUser user(Long id, String username, String status) {
        SysUser user = new SysUser();
        user.setUserId(id);
        user.setUsername(username);
        user.setStatus(status);
        return user;
    }

    private static UserFaceTemplate encryptedTemplate(Long userId, double[] embedding) throws Exception {
        byte[] key = MessageDigest.getInstance("SHA-256")
                .digest(ENCRYPTION_KEY.getBytes(StandardCharsets.UTF_8));
        ByteBuffer plain = ByteBuffer.allocate(Integer.BYTES + embedding.length * Double.BYTES);
        plain.putInt(embedding.length);
        for (double value : embedding) plain.putDouble(value);
        byte[] nonce = new byte[12];
        nonce[0] = (byte) userId.intValue();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(128, nonce));
        byte[] encrypted = cipher.doFinal(plain.array());
        UserFaceTemplate template = new UserFaceTemplate();
        template.setUserId(userId);
        template.setEmbeddingCiphertext(ByteBuffer.allocate(nonce.length + encrypted.length)
                .put(nonce).put(encrypted).array());
        return template;
    }
}
