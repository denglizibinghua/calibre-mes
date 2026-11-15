package com.bikeassembly.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bikeassembly.dto.FaceLoginDTO;
import com.bikeassembly.dto.FaceTemplateDTO;
import com.bikeassembly.entity.SysUser;
import com.bikeassembly.entity.UserFaceTemplate;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.mapper.SysUserMapper;
import com.bikeassembly.mapper.UserFaceTemplateMapper;
import com.bikeassembly.service.impl.LoginFailService;
import com.bikeassembly.vo.UserLoginVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Service
public class FaceAuthService {
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final FaceEmbeddingClient embeddingClient;
    private final UserFaceTemplateMapper templateMapper;
    private final SysUserMapper userMapper;
    private final SysUserService userService;
    private final LoginFailService loginFailService;
    private final byte[] encryptionKey;
    private final double matchThreshold;
    private final double ambiguityMargin;
    private final SecureRandom secureRandom = new SecureRandom();

    public FaceAuthService(FaceEmbeddingClient embeddingClient,
                           UserFaceTemplateMapper templateMapper,
                           SysUserMapper userMapper,
                           SysUserService userService,
                           LoginFailService loginFailService,
                           @Value("${ai.face.encryption-key:${FACE_TEMPLATE_ENCRYPTION_KEY:}}")
                           String configuredKey,
                           @Value("${ai.face.match-threshold:${FACE_MATCH_THRESHOLD:0.42}}")
                           double matchThreshold,
                           @Value("${ai.face.ambiguity-margin:${FACE_AMBIGUITY_MARGIN:0.06}}")
                           double ambiguityMargin) {
        this.embeddingClient = embeddingClient;
        this.templateMapper = templateMapper;
        this.userMapper = userMapper;
        this.userService = userService;
        this.loginFailService = loginFailService;
        this.encryptionKey = deriveKey(configuredKey);
        if (!Double.isFinite(matchThreshold) || matchThreshold < 0 || matchThreshold > 1) {
            throw new IllegalArgumentException("人脸匹配阈值必须在 0 到 1 之间");
        }
        if (!Double.isFinite(ambiguityMargin) || ambiguityMargin < 0 || ambiguityMargin > 2) {
            throw new IllegalArgumentException("人脸歧义阈值必须在 0 到 2 之间");
        }
        this.matchThreshold = matchThreshold;
        this.ambiguityMargin = ambiguityMargin;
    }

    public void register(Long userId, FaceTemplateDTO dto) {
        requireKey();
        if (dto == null || !Boolean.TRUE.equals(dto.getConsent())) {
            throw new BaseException("注册人脸模板前必须明确同意");
        }
        ensureUser(userId);
        double[] embedding = embeddingClient.extract(dto.getImageBase64()).embedding();
        UserFaceTemplate template = new UserFaceTemplate();
        template.setUserId(userId);
        template.setEmbeddingCiphertext(encrypt(embedding));
        template.setConsentedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());
        templateMapper.upsertTemplate(template);
    }

    public void revoke(Long userId) {
        templateMapper.deleteById(userId);
    }

    public boolean isRegistered(Long userId) {
        return userId != null && templateMapper.selectById(userId) != null;
    }

    public UserLoginVO login(FaceLoginDTO dto, String clientType) {
        requireKey();
        double[] probe = embeddingClient.extract(dto.getImageBase64()).embedding();
        if (dto.getUsername() != null && !dto.getUsername().isBlank()) {
            SysUser user = userMapper.findByUsername(dto.getUsername().trim());
            if (user == null) throw new BaseException("账号不存在");
            UserFaceTemplate template = templateMapper.selectById(user.getUserId());
            if (template == null || cosine(probe, decrypt(template.getEmbeddingCiphertext())) < matchThreshold) {
                throw new BaseException("人脸与账号不匹配");
            }
            ensureNotLocked(user);
            return userService.faceLogin(user.getUserId(), clientType);
        }
        List<UserFaceTemplate> templates = templateMapper.selectList(null);
        List<Match> matches = new ArrayList<>();
        for (UserFaceTemplate template : templates) {
            if (template.getUserId() == null) continue;
            SysUser user = userMapper.selectById(template.getUserId());
            if (user == null || "1".equals(user.getStatus())) continue;
            matches.add(new Match(template.getUserId(), user.getUsername(),
                    cosine(probe, decrypt(template.getEmbeddingCiphertext()))));
        }
        matches.sort(Comparator.comparingDouble(Match::score).reversed());
        Match best = matches.isEmpty() ? null : matches.get(0);
        if (best == null || best.score() < matchThreshold) throw new BaseException("未识别到已注册用户");
        double second = matches.size() > 1 ? matches.get(1).score() : -1;
        if (second >= 0 && best.score() - second < ambiguityMargin) {
            throw new BaseException("人脸匹配结果存在歧义，请使用账号 + 人脸核验");
        }
        if (loginFailService.isLocked(best.username())) {
            throw new BaseException("账号已被锁定，请稍后再试");
        }
        return userService.faceLogin(best.userId(), clientType);
    }

    private void ensureNotLocked(SysUser user) {
        if (loginFailService.isLocked(user.getUsername())) {
            throw new BaseException("账号已被锁定，请稍后再试");
        }
    }

    private SysUser ensureUser(Long userId) {
        if (userId == null) throw new BaseException("未获取当前用户");
        SysUser user = userMapper.selectById(userId);
        if (user == null) throw new BaseException("用户不存在");
        if ("1".equals(user.getStatus())) throw new BaseException("账号已停用");
        return user;
    }

    private void requireKey() {
        if (encryptionKey == null) throw new BaseException("未配置人脸模板加密密钥，已禁用人脸功能");
    }

    private byte[] encrypt(double[] values) {
        try {
            ByteBuffer plainBuffer = ByteBuffer.allocate(Integer.BYTES + values.length * Double.BYTES);
            plainBuffer.putInt(values.length);
            for (double value : values) plainBuffer.putDouble(value);
            byte[] plain = plainBuffer.array();
            byte[] nonce = new byte[NONCE_BYTES];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plain);
            return ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array();
        } catch (Exception e) {
            throw new BaseException("加密人脸模板失败");
        }
    }

    private double[] decrypt(byte[] value) {
        try {
            if (value == null || value.length <= NONCE_BYTES) throw new BaseException("人脸模板无效");
            byte[] nonce = Arrays.copyOf(value, NONCE_BYTES);
            byte[] encrypted = Arrays.copyOfRange(value, NONCE_BYTES, value.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            ByteBuffer buffer = ByteBuffer.wrap(cipher.doFinal(encrypted));
            int size = buffer.getInt();
            if (size != 128) throw new BaseException("人脸模板维度无效");
            double[] result = new double[size];
            for (int i = 0; i < size; i++) {
                result[i] = buffer.getDouble();
                if (!Double.isFinite(result[i])) throw new BaseException("人脸模板包含无效数值");
            }
            return result;
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            throw new BaseException("人脸模板解密失败，已拒绝登录");
        }
    }

    private double cosine(double[] a, double[] b) {
        if (a.length != b.length) return -1;
        double dot = 0, an = 0, bn = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            an += a[i] * a[i];
            bn += b[i] * b[i];
        }
        return an == 0 || bn == 0 ? -1 : dot / (Math.sqrt(an) * Math.sqrt(bn));
    }

    private byte[] deriveKey(String raw) {
        if (raw == null || raw.isBlank()
                || raw.getBytes(StandardCharsets.UTF_8).length < 32) return null;
        try {
            return MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private record Match(Long userId, String username, double score) {
    }
}
