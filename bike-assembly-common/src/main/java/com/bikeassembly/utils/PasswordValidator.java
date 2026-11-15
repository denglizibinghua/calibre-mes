package com.bikeassembly.utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 密码复杂度校验工具 — V3.3。
 * 规则可配置，校验失败返回具体原因。
 */
public final class PasswordValidator {

    private PasswordValidator() {}

    // 弱密码黑名单
    private static final Set<String> WEAK_PASSWORDS = new HashSet<>(Arrays.asList(
            "123456", "12345678", "123456789", "password", "Password",
            "admin123", "qwerty123", "11111111", "88888888",
            "abc123456", "a12345678", "Aa123456"
    ));

    public static class Result {
        public final boolean valid;
        public final String message;

        private Result(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public static Result ok() { return new Result(true, null); }
        public static Result fail(String msg) { return new Result(false, msg); }
    }

    /**
     * 校验密码复杂度（不含历史密码检查，由调用方自行处理）。
     */
    public static Result validate(String password, String username,
                                   int minLength, int maxLength) {
        if (password == null || password.isBlank()) {
            return Result.fail("密码不能为空");
        }
        if (password.length() < minLength) {
            return Result.fail("密码长度不能少于" + minLength + "位");
        }
        if (password.length() > maxLength) {
            return Result.fail("密码长度不能超过" + maxLength + "位");
        }
        if (!password.matches(".*\\d.*")) {
            return Result.fail("密码必须包含数字");
        }
        if (!password.matches(".*[a-z].*")) {
            return Result.fail("密码必须包含小写字母");
        }
        if (!password.matches(".*[A-Z].*")) {
            return Result.fail("密码必须包含大写字母");
        }
        if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?`~].*")) {
            return Result.fail("密码必须包含特殊字符");
        }
        if (username != null && !username.isBlank()
                && password.toLowerCase().contains(username.toLowerCase())) {
            return Result.fail("密码不能包含用户名");
        }
        if (WEAK_PASSWORDS.contains(password)) {
            return Result.fail("密码过于常见，请使用更复杂的密码");
        }
        return Result.ok();
    }
}
