package com.bikeassembly.enums;

import lombok.Getter;

/**
 * 工序操作枚举 — 定义每个操作对应的状态转换。
 */
@Getter
public enum WorkProcessAction {

    MARK_READY("mark_ready", "可开工", WorkProcessStatus.PENDING, WorkProcessStatus.READY),
    START("start", "开始工作", WorkProcessStatus.READY, WorkProcessStatus.IN_PROGRESS),
    REPORT_COMPLETE("report_complete", "报工完成", WorkProcessStatus.IN_PROGRESS, WorkProcessStatus.COMPLETED),
    REPORT_WITH_QC("report_with_qc", "报工待检", WorkProcessStatus.IN_PROGRESS, WorkProcessStatus.WAIT_INSPECTION),
    QC_PASS("qc_pass", "检验合格", WorkProcessStatus.WAIT_INSPECTION, WorkProcessStatus.QUALIFIED),
    QC_FAIL("qc_fail", "检验不合格", WorkProcessStatus.WAIT_INSPECTION, WorkProcessStatus.REJECTED),
    QUALIFIED_COMPLETE("qualified_complete", "合格确认", WorkProcessStatus.QUALIFIED, WorkProcessStatus.COMPLETED),
    REWORK_START("rework_start", "开始返工", WorkProcessStatus.REJECTED, WorkProcessStatus.REWORK),
    REWORK_COMPLETE("rework_complete", "返工完成", WorkProcessStatus.REWORK, WorkProcessStatus.WAIT_INSPECTION),
    SKIP("skip", "跳过", null, WorkProcessStatus.SKIPPED),
    CANCEL("cancel", "取消", null, WorkProcessStatus.CANCELLED);

    private final String action;
    private final String label;
    private final WorkProcessStatus expectedFrom;
    private final WorkProcessStatus targetTo;

    WorkProcessAction(String action, String label,
                      WorkProcessStatus expectedFrom, WorkProcessStatus targetTo) {
        this.action = action;
        this.label = label;
        this.expectedFrom = expectedFrom;
        this.targetTo = targetTo;
    }
}
