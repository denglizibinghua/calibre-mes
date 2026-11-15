package com.bikeassembly.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 质检单状态枚举。
 * 状态机：待检(0) → 已录入(1) → 已确认(2)
 */
@Getter
public enum InspectionStatus {

    PENDING("0", "待检"),
    ENTERED("1", "已录入"),
    CONFIRMED("2", "已确认");

    private final String code;
    private final String label;

    private static final Map<String, InspectionStatus> CODE_MAP =
            Arrays.stream(values()).collect(Collectors.toMap(InspectionStatus::getCode, Function.identity()));

    InspectionStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static InspectionStatus fromCode(String code) {
        InspectionStatus status = CODE_MAP.get(code);
        if (status == null) {
            throw new IllegalArgumentException("未知的质检状态码: " + code);
        }
        return status;
    }

    public boolean isTerminal() {
        return this == CONFIRMED;
    }
}
