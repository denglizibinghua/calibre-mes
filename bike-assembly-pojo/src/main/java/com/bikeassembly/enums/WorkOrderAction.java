package com.bikeassembly.enums;

import lombok.Getter;

/**
 * 工单操作枚举 — 定义每个操作对应的状态转换。
 */
@Getter
public enum WorkOrderAction {

    RELEASE("release", "下达", WorkOrderStatus.CREATED, WorkOrderStatus.RELEASED),
    PAUSE("pause", "暂停", WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.PAUSED),
    RESUME("resume", "恢复", WorkOrderStatus.PAUSED, WorkOrderStatus.IN_PROGRESS),
    CLOSE("close", "关闭", WorkOrderStatus.COMPLETED, WorkOrderStatus.CLOSED),
    CANCEL("cancel", "取消", null, WorkOrderStatus.CANCELLED);

    private final String action;
    private final String label;
    private final WorkOrderStatus expectedFrom;
    private final WorkOrderStatus targetTo;

    WorkOrderAction(String action, String label,
                    WorkOrderStatus expectedFrom, WorkOrderStatus targetTo) {
        this.action = action;
        this.label = label;
        this.expectedFrom = expectedFrom;
        this.targetTo = targetTo;
    }
}
