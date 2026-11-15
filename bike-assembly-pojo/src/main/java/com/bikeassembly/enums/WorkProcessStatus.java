package com.bikeassembly.enums;

import lombok.Getter;

/**
 * 工序流转状态枚举。
 * CHAR(1) 编码，向后兼容现有数据。
 */
/**
 * [答辩重点] 工序流转状态枚举 —— 工序流转状态机的核心状态定义。
 * 数据库中 status 字段为 CHAR(1)，状态码与中文标签一一对应。
 *
 * 完整流转路径（由 StatusTransitionValidator 集中定义）：
 *   待开始(0) → 可开工(9) → 进行中(1) → 待检(3) 或 已完成(2)
 *   待检(3) → 检验合格(4) 或 检验不合格(5)
 *   检验合格(4) → 已完成(2)
 *   检验不合格(5) → 返工中(6) → 待检(3)（循环，返工完成后必须复检）
 *   待开始(0) 和 可开工(9) 可跳过为已跳过(7)
 *   待开始(0) 可取消为已取消(8)
 *   已完成(2)、已跳过(7)、已取消(8) 为终态
 */
@Getter
public enum WorkProcessStatus {

    /** "0" - 待开始：工序记录创建后的初始状态 */
    PENDING("0", "待开始"),
    /** "1" - 进行中：扫码开工后进入，可以报工/送检 */
    IN_PROGRESS("1", "进行中"),
    /** "2" - 已完成：无需质检直接报工完成，或质检通过后自动完成。终态 */
    COMPLETED("2", "已完成"),
    /** "3" - 待检：报工送检后等待质检员判定 */
    WAIT_INSPECTION("3", "待检"),
    /** "4" - 检验合格：质检员判定合格，自动流转到已完成 */
    QUALIFIED("4", "检验合格"),
    /** "5" - 检验不合格：质检员判定不合格，可发起返工 */
    REJECTED("5", "检验不合格"),
    /** "6" - 返工中：质检不合格后发起返工，完成后必须重新送检 */
    REWORK("6", "返工中"),
    /** "7" - 已跳过：终态，跳过的工序不影响后续流转 */
    SKIPPED("7", "已跳过"),
    /** "8" - 已取消：终态 */
    CANCELLED("8", "已取消"),
    /** "9" - 可开工：前序工序完成后自动标记，表示可以扫码开工 */
    READY("9", "可开工");

    private final String code;
    private final String label;

    WorkProcessStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static WorkProcessStatus fromCode(String code) {
        for (WorkProcessStatus s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知工序状态码: " + code);
    }

    /**
     * 是否为终端状态 — 不可再做任何流转。
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == SKIPPED || this == CANCELLED;
    }

    /**
     * 是否可开始工作（手动开工）。
     */
    public boolean isStartable() {
        return this == READY;
    }
}
