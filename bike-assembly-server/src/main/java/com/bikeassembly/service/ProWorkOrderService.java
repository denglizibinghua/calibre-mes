package com.bikeassembly.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bikeassembly.dto.ProWorkOrderDTO;
import com.bikeassembly.entity.ProWorkOrder;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.vo.ProWorkOrderVO;

public interface ProWorkOrderService extends IService<ProWorkOrder> {

    //新建工单
    ProWorkOrderVO saveProWorkOrder(ProWorkOrderDTO dto);

    ProWorkOrderVO updateProWorkOrder(Long id, ProWorkOrderDTO dto);

    ProWorkOrderVO getProWorkOrderById(Long id);

    PageResult<ProWorkOrderVO> listProWorkOrders(Long page, Long size, String workOrderNo, String orderNo, String productName, String scope);

    void deleteProWorkOrder(Long id);

    /** 指派执行人 */
    ProWorkOrderVO assignAssignee(Long workOrderId, Long userId);

    /** 更换执行人 */
    ProWorkOrderVO reassignAssignee(Long workOrderId, Long userId);

    /** 取消指派 */
    ProWorkOrderVO unassignAssignee(Long workOrderId);

    /** 获取启用的用户列表（用于执行人选择） */
    java.util.List<com.bikeassembly.vo.SimpleUserVO> getActiveUsersForAssignment();

    // ==================== 状态机操作 ====================

    /** 下达工单：CREATED → RELEASED */
    ProWorkOrderVO releaseWorkOrder(Long workOrderId);

    /** 暂停工单：IN_PROGRESS → PAUSED */
    ProWorkOrderVO pauseWorkOrder(Long workOrderId);

    /** 恢复工单：PAUSED → IN_PROGRESS */
    ProWorkOrderVO resumeWorkOrder(Long workOrderId);

    /** 关闭工单：COMPLETED → CLOSED */
    ProWorkOrderVO closeWorkOrder(Long workOrderId);

    /** 取消工单：CREATED 或 RELEASED → CANCELLED */
    ProWorkOrderVO cancelWorkOrder(Long workOrderId, String remark);
}
