package com.bikeassembly.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * V3.3 状态机单元测试 — 验证所有状态转换规则。
 */
class StatusTransitionValidatorTest {

    // ==================== 工单状态转换 ====================

    @Test void workOrder_createdToReleased() {
        assertTrue(StatusTransitionValidator.canTransition(WorkOrderStatus.CREATED, WorkOrderStatus.RELEASED));
    }
    @Test void workOrder_createdToCancelled() {
        assertTrue(StatusTransitionValidator.canTransition(WorkOrderStatus.CREATED, WorkOrderStatus.CANCELLED));
    }
    @Test void workOrder_releasedToInProgress() {
        assertTrue(StatusTransitionValidator.canTransition(WorkOrderStatus.RELEASED, WorkOrderStatus.IN_PROGRESS));
    }
    @Test void workOrder_releasedToCancelled() {
        assertTrue(StatusTransitionValidator.canTransition(WorkOrderStatus.RELEASED, WorkOrderStatus.CANCELLED));
    }
    @Test void workOrder_inProgressToPaused() {
        assertTrue(StatusTransitionValidator.canTransition(WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.PAUSED));
    }
    @Test void workOrder_inProgressToCompleted() {
        assertTrue(StatusTransitionValidator.canTransition(WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.COMPLETED));
    }
    @Test void workOrder_pausedToInProgress() {
        assertTrue(StatusTransitionValidator.canTransition(WorkOrderStatus.PAUSED, WorkOrderStatus.IN_PROGRESS));
    }
    @Test void workOrder_completedToClosed() {
        assertTrue(StatusTransitionValidator.canTransition(WorkOrderStatus.COMPLETED, WorkOrderStatus.CLOSED));
    }
    @Test void workOrder_createdToInProgress_deny() {
        assertFalse(StatusTransitionValidator.canTransition(WorkOrderStatus.CREATED, WorkOrderStatus.IN_PROGRESS));
    }
    @Test void workOrder_closedTerminal() {
        for (WorkOrderStatus s : WorkOrderStatus.values()) {
            assertFalse(StatusTransitionValidator.canTransition(WorkOrderStatus.CLOSED, s));
        }
    }
    @Test void workOrder_cancelledTerminal() {
        for (WorkOrderStatus s : WorkOrderStatus.values()) {
            assertFalse(StatusTransitionValidator.canTransition(WorkOrderStatus.CANCELLED, s));
        }
    }

    // ==================== 工序状态转换 ====================

    @Test void process_pendingToReady() {
        assertTrue(StatusTransitionValidator.canTransition(WorkProcessStatus.PENDING, WorkProcessStatus.READY));
    }
    @Test void process_pendingToSkipped() {
        assertTrue(StatusTransitionValidator.canTransition(WorkProcessStatus.PENDING, WorkProcessStatus.SKIPPED));
    }
    @Test void process_pendingToCancelled() {
        assertTrue(StatusTransitionValidator.canTransition(WorkProcessStatus.PENDING, WorkProcessStatus.CANCELLED));
    }
    @Test void process_readyToInProgress() {
        assertTrue(StatusTransitionValidator.canTransition(WorkProcessStatus.READY, WorkProcessStatus.IN_PROGRESS));
    }
    @Test void process_readyToSkipped() {
        assertTrue(StatusTransitionValidator.canTransition(WorkProcessStatus.READY, WorkProcessStatus.SKIPPED));
    }
    @Test void process_inProgressToWaitInspection() {
        assertTrue(StatusTransitionValidator.canTransition(
                WorkProcessStatus.IN_PROGRESS, WorkProcessStatus.WAIT_INSPECTION));
    }
    @Test void process_inProgressToCompleted() {
        assertTrue(StatusTransitionValidator.canTransition(WorkProcessStatus.IN_PROGRESS, WorkProcessStatus.COMPLETED));
    }
    @Test void process_waitInspectionToQualified() {
        assertTrue(StatusTransitionValidator.canTransition(WorkProcessStatus.WAIT_INSPECTION, WorkProcessStatus.QUALIFIED));
    }
    @Test void process_waitInspectionToRejected() {
        assertTrue(StatusTransitionValidator.canTransition(WorkProcessStatus.WAIT_INSPECTION, WorkProcessStatus.REJECTED));
    }
    @Test void process_rejectedToRework() {
        assertTrue(StatusTransitionValidator.canTransition(WorkProcessStatus.REJECTED, WorkProcessStatus.REWORK));
    }
    @Test void process_reworkToWaitInspection() {
        assertTrue(StatusTransitionValidator.canTransition(WorkProcessStatus.REWORK, WorkProcessStatus.WAIT_INSPECTION));
    }
    @Test void process_qualifiedToCompleted() {
        assertTrue(StatusTransitionValidator.canTransition(WorkProcessStatus.QUALIFIED, WorkProcessStatus.COMPLETED));
    }
    @Test void process_pendingToInProgress_deny() {
        assertFalse(StatusTransitionValidator.canTransition(WorkProcessStatus.PENDING, WorkProcessStatus.IN_PROGRESS));
    }
    @Test void process_reworkToCompleted_deny() {
        // 返工后必须重新进入待检，不可直接完成
        assertFalse(StatusTransitionValidator.canTransition(WorkProcessStatus.REWORK, WorkProcessStatus.COMPLETED));
    }
    @Test void process_completedTerminal() {
        for (WorkProcessStatus s : WorkProcessStatus.values()) {
            assertFalse(StatusTransitionValidator.canTransition(WorkProcessStatus.COMPLETED, s));
        }
    }
    @Test void process_skippedTerminal() {
        for (WorkProcessStatus s : WorkProcessStatus.values()) {
            assertFalse(StatusTransitionValidator.canTransition(WorkProcessStatus.SKIPPED, s));
        }
    }
    @Test void process_cancelledTerminal() {
        for (WorkProcessStatus s : WorkProcessStatus.values()) {
            assertFalse(StatusTransitionValidator.canTransition(WorkProcessStatus.CANCELLED, s));
        }
    }

    // ==================== 枚举工具方法 ====================

    @Test void workOrderStatus_terminalFlags() {
        assertTrue(WorkOrderStatus.CLOSED.isTerminal());
        assertTrue(WorkOrderStatus.CANCELLED.isTerminal());
        assertFalse(WorkOrderStatus.CREATED.isTerminal());
    }
    @Test void workOrderStatus_productionActive() {
        assertTrue(WorkOrderStatus.RELEASED.isProductionActive());
        assertTrue(WorkOrderStatus.IN_PROGRESS.isProductionActive());
        assertFalse(WorkOrderStatus.PAUSED.isProductionActive());
    }
    @Test void workProcessStatus_terminalFlags() {
        assertTrue(WorkProcessStatus.COMPLETED.isTerminal());
        assertTrue(WorkProcessStatus.SKIPPED.isTerminal());
        assertTrue(WorkProcessStatus.CANCELLED.isTerminal());
    }
    @Test void fromCode_validAndInvalid() {
        assertEquals(WorkOrderStatus.CREATED, WorkOrderStatus.fromCode("0"));
        assertEquals(WorkProcessStatus.WAIT_INSPECTION, WorkProcessStatus.fromCode("3"));
        assertThrows(IllegalArgumentException.class, () -> WorkOrderStatus.fromCode("X"));
        assertThrows(IllegalArgumentException.class, () -> WorkProcessStatus.fromCode("Z"));
    }
}
