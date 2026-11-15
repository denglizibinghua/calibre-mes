package com.bikeassembly.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 启动时校验 JWT 密钥非空且满足最低长度要求。
 * dev profile 下跳过校验（使用 application-dev.yml 中的占位密钥）。
 */
@Component
@Slf4j
@Profile("!dev")
public class JwtSecretValidator implements CommandLineRunner {

    @Value("${bike.jwt.admin.secret-key}")
    private String adminSecret;

    @Value("${bike.jwt.app.secret-key}")
    private String appSecret;

    private static final int MIN_SECRET_LENGTH = 32;

    @Override
    public void run(String... args) {
        validateSecret(adminSecret, "JWT_ADMIN_SECRET");
        validateSecret(appSecret, "JWT_APP_SECRET");
        log.info("JWT密钥校验通过");
    }

    private void validateSecret(String secret, String name) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    name + " 未设置。生产环境必须通过环境变量提供。开发环境请使用 dev profile。");
        }
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    name + " 长度不足 " + MIN_SECRET_LENGTH + " 字符，请使用更强的密钥。");
        }
        // 拒绝 dev profile 的占位密钥在非 dev 环境下使用
        if (secret.contains("dev-") || secret.contains("do-not-use") || secret.contains("placeholder")) {
            throw new IllegalStateException(
                    name + " 包含已知不安全占位值，请设置真实生产密钥。");
        }
    }
}
