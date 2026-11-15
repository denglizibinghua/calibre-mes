package com.bikeassembly.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class PasswordValidatorTest {
    static Stream<Arguments> invalidPasswords() {
        return Stream.of(
                Arguments.of(null, "密码不能为空"),
                Arguments.of("", "密码不能为空"),
                Arguments.of("   ", "密码不能为空"),
                Arguments.of("Aa1!bbb", "密码长度不能少于8位"),
                Arguments.of("Aa1!" + "b".repeat(29), "密码长度不能超过32位"),
                Arguments.of("Abcd!xyz", "密码必须包含数字"),
                Arguments.of("ABCD1!XY", "密码必须包含小写字母"),
                Arguments.of("abcd1!xy", "密码必须包含大写字母"),
                Arguments.of("Abcd1234", "密码必须包含特殊字符"));
    }

    @ParameterizedTest
    @MethodSource("invalidPasswords")
    void rejectsInvalidEquivalenceClasses(String password, String expectedMessage) {
        PasswordValidator.Result result = PasswordValidator.validate(password, "operator", 8, 32);
        assertFalse(result.valid);
        assertEquals(expectedMessage, result.message);
    }

    static Stream<String> validBoundaryPasswords() {
        return Stream.of("Aa1!bbbb", "Aa1!" + "b".repeat(27), "Aa1!" + "b".repeat(28));
    }

    @ParameterizedTest
    @MethodSource("validBoundaryPasswords")
    void acceptsMinimumMaximumAndInsideBoundary(String password) {
        PasswordValidator.Result result = PasswordValidator.validate(password, "operator", 8, 32);
        assertTrue(result.valid);
        assertNull(result.message);
    }

    static Stream<Arguments> usernames() {
        return Stream.of(Arguments.of((String) null, true), Arguments.of("", true),
                Arguments.of(" ", true), Arguments.of("other", true),
                Arguments.of("abcd", false), Arguments.of("ABCD", false));
    }

    @ParameterizedTest
    @MethodSource("usernames")
    void checksReachableUsernameShortCircuitCases(String username, boolean expected) {
        assertEquals(expected, PasswordValidator.validate("Abcd1!xy", username, 8, 32).valid);
    }

    @Test
    void rejectsCommonPasswordEvenWhenOtherRulesRejectFirst() {
        assertFalse(PasswordValidator.validate("Aa123456", "operator", 8, 32).valid);
    }
}
