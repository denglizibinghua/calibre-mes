package com.bikeassembly.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 质检结果枚举。
 * 统一 pro_work_process.qc_result 字段的取值。
 */
@Getter
public enum QcResult {

    PENDING("0", "待检"),
    PASS("1", "合格"),
    FAIL("2", "不合格"),
    RECHECK("3", "待复检");

    private final String code;
    private final String label;

    private static final Map<String, QcResult> CODE_MAP =
            Arrays.stream(values()).collect(Collectors.toMap(QcResult::getCode, Function.identity()));

    QcResult(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static QcResult fromCode(String code) {
        QcResult result = CODE_MAP.get(code);
        if (result == null) {
            throw new IllegalArgumentException("未知的质检结果码: " + code);
        }
        return result;
    }

    /**
     * 是否表示合格结果。
     */
    public boolean isPass() {
        return this == PASS;
    }

    /**
     * 是否表示不合格结果。
     */
    public boolean isFail() {
        return this == FAIL;
    }
}
