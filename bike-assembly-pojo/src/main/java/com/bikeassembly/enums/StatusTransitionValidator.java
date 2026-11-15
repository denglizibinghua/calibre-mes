package com.bikeassembly.enums;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 集中式状态转换校验器。
 * <p>
 * 所有允许的状态转换在此处以静态 Map 集中定义，
 * 禁止在 ServiceImpl 中散落 if/else 状态判断。
 * </p>
 */
public final class StatusTransitionValidator {

    private StatusTransitionValidator() {
    }

    // ==================== 工单状态转换规则 ====================

    private static final Map<WorkOrderStatus, Set<WorkOrderStatus>> WORK_ORDER_TRANSITIONS =
            new EnumMap<>(WorkOrderStatus.class);

    static {
        WORK_ORDER_TRANSITIONS.put(WorkOrderStatus.CREATED,
                EnumSet.of(WorkOrderStatus.RELEASED, WorkOrderStatus.CANCELLED));
        WORK_ORDER_TRANSITIONS.put(WorkOrderStatus.RELEASED,
                EnumSet.of(WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.CANCELLED));
        WORK_ORDER_TRANSITIONS.put(WorkOrderStatus.IN_PROGRESS,
                EnumSet.of(WorkOrderStatus.PAUSED, WorkOrderStatus.COMPLETED));
        WORK_ORDER_TRANSITIONS.put(WorkOrderStatus.PAUSED,
                EnumSet.of(WorkOrderStatus.IN_PROGRESS));
        WORK_ORDER_TRANSITIONS.put(WorkOrderStatus.COMPLETED,
                EnumSet.of(WorkOrderStatus.CLOSED));
        // CLOSED: terminal — no outbound transitions
        // CANCELLED: terminal — no outbound transitions
    }

    // ==================== 工序状态转换规则 ====================

    private static final Map<WorkProcessStatus, Set<WorkProcessStatus>> WORK_PROCESS_TRANSITIONS =
            new EnumMap<>(WorkProcessStatus.class);

    static {
        WORK_PROCESS_TRANSITIONS.put(WorkProcessStatus.PENDING,
                EnumSet.of(WorkProcessStatus.READY, WorkProcessStatus.SKIPPED, WorkProcessStatus.CANCELLED));
        WORK_PROCESS_TRANSITIONS.put(WorkProcessStatus.READY,
                EnumSet.of(WorkProcessStatus.IN_PROGRESS, WorkProcessStatus.SKIPPED));
        WORK_PROCESS_TRANSITIONS.put(WorkProcessStatus.IN_PROGRESS,
                EnumSet.of(WorkProcessStatus.WAIT_INSPECTION, WorkProcessStatus.COMPLETED));
        WORK_PROCESS_TRANSITIONS.put(WorkProcessStatus.WAIT_INSPECTION,
                EnumSet.of(WorkProcessStatus.QUALIFIED, WorkProcessStatus.REJECTED));
        WORK_PROCESS_TRANSITIONS.put(WorkProcessStatus.REJECTED,
                EnumSet.of(WorkProcessStatus.REWORK));
        WORK_PROCESS_TRANSITIONS.put(WorkProcessStatus.REWORK,
                EnumSet.of(WorkProcessStatus.WAIT_INSPECTION));
        WORK_PROCESS_TRANSITIONS.put(WorkProcessStatus.QUALIFIED,
                EnumSet.of(WorkProcessStatus.COMPLETED));
        // COMPLETED, SKIPPED, CANCELLED: terminal — no outbound transitions
    }

    // ==================== 质检单状态转换规则 ====================

    private static final Map<InspectionStatus, Set<InspectionStatus>> INSPECTION_TRANSITIONS =
            new EnumMap<>(InspectionStatus.class);

    static {
        INSPECTION_TRANSITIONS.put(InspectionStatus.PENDING,
                EnumSet.of(InspectionStatus.ENTERED));
        INSPECTION_TRANSITIONS.put(InspectionStatus.ENTERED,
                EnumSet.of(InspectionStatus.CONFIRMED));
        // CONFIRMED: terminal — no outbound transitions
    }

    // ==================== 公开校验方法 ====================

    /**
     * 校验工单状态转换是否合法。
     */
    public static boolean canTransition(WorkOrderStatus from, WorkOrderStatus to) {
        Set<WorkOrderStatus> allowed = WORK_ORDER_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    /**
     * 校验工序状态转换是否合法。
     */
    public static boolean canTransition(WorkProcessStatus from, WorkProcessStatus to) {
        Set<WorkProcessStatus> allowed = WORK_PROCESS_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    /**
     * 校验质检单状态转换是否合法。
     */
    public static boolean canTransition(InspectionStatus from, InspectionStatus to) {
        Set<InspectionStatus> allowed = INSPECTION_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }
}
