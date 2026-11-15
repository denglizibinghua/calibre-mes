package com.bikeassembly.enums;

import lombok.Getter;

/**
 * 生产工单状态枚举。
 * CHAR(1) 编码，向后兼容现有数据。
 */
/**
 * [答辩重点] 生产工单状态枚举 —— 工序流转状态机的顶层状态。
 * 数据库中 status 字段为 CHAR(1)，状态码与中文标签一一对应。
 *
 * 状态流转规则（由 StatusTransitionValidator 集中定义）：
 *   已创建(0) → 已下达(4) → 生产中(1) → 已完工(2) → 已关闭(3)
 *   已创建(0) 和 已下达(4) 可取消(6)
 *   生产中(1) 可暂停(5)，暂停(5) 可恢复为生产(1)
 *   已关闭(3) 和 已取消(6) 为终态，不可再流转
 */
@Getter
public enum WorkOrderStatus {

    /** "0" - 已创建：工单初始状态，可下达或取消 */
    CREATED("0", "已创建"),
    /** "1" - 生产中：首道工序开工后自动转入，可暂停或完工 */
    IN_PROGRESS("1", "生产中"),
    /** "2" - 已完工：末道工序完成后自动转入，可关闭 */
    COMPLETED("2", "已完工"),
    /** "3" - 已关闭：终态，不可再流转 */
    CLOSED("3", "已关闭"),
    /** "4" - 已下达：可开始生产，首道工序开工后自动转入生产中 */
    RELEASED("4", "已下达"),
    /** "5" - 已暂停：生产暂停，可恢复 */
    PAUSED("5", "已暂停"),
    /** "6" - 已取消：终态，不可再流转 */
    CANCELLED("6", "已取消");

    private final String code;
    private final String label;

    WorkOrderStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static WorkOrderStatus fromCode(String code) {
        for (WorkOrderStatus s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知工单状态码: " + code);
    }

    /**
     * 是否为终端状态 — 不可再做任何流转。
     */
    public boolean isTerminal() {
        return this == CLOSED || this == CANCELLED;
    }

    /**
     * 是否允许生产操作（报工、开工等）。
     */
    public boolean isProductionActive() {
        return this == RELEASED || this == IN_PROGRESS;
    }
}
