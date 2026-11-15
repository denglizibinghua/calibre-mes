package com.bikeassembly.controller.admin;

import com.bikeassembly.annotation.IdempotencyRequired;
import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.PermissionConstant;
import com.bikeassembly.dto.ProWorkOrderDTO;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.ProWorkOrderService;
import com.bikeassembly.validation.OnCreate;
import com.bikeassembly.vo.ProWorkOrderVO;
import com.bikeassembly.vo.SimpleUserVO;
import jakarta.validation.groups.Default;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/pro-work-order")
@Slf4j
public class ProWorkOrderController {

    // ========== 工单 ==========

    @Autowired
    private ProWorkOrderService proWorkOrderService;

    @Autowired
    private com.bikeassembly.service.WorkOrderScanService workOrderScanService;

    @GetMapping("/{id}/qr-code")
    @RequirePermission(PermissionConstant.ADMIN_WORK_ORDER_VIEW)
    public Result<com.bikeassembly.vo.WorkOrderQrCodeVO> qrCode(@PathVariable Long id) {
        return Result.success(workOrderScanService.getQrCode(id));
    }

    /**
     * [答辩入口] 工单分页查询接口。
     * 完整路径：GET /admin/pro-work-order/list
     * [请求参数] page（默认1）、size（默认15）、orderNo（可选，订单号模糊查询）、productName（可选，产品名称模糊查询）
     * [调用链] Controller → ProWorkOrderService.listProWorkOrders → ProWorkOrderMapper.selectPageByCondition（动态 SQL）
     * [数据库操作] 分页查询 pro_work_order 表，LEFT JOIN base_item 表按产品名称过滤
     * [前端调用] workOrder.vue 的 fetchList() → getProWorkOrderList() → GET /admin/pro-work-order/list
     */
    @GetMapping("/list")
    @RequirePermission(PermissionConstant.ADMIN_WORK_ORDER_VIEW)
    public Result<PageResult<ProWorkOrderVO>> list(@RequestParam(defaultValue = "1") Long page,
                                                    @RequestParam(defaultValue = "15") Long size,
                                                    @RequestParam(required = false) String workOrderNo,
                                                    @RequestParam(required = false) String orderNo,
                                                    @RequestParam(required = false) String productName) {
        PageResult<ProWorkOrderVO> pageResult = proWorkOrderService.listProWorkOrders(
                page, size, workOrderNo, orderNo, productName, "all");
        return Result.success(pageResult);
    }

    @GetMapping("/{id}")
    @RequirePermission(PermissionConstant.ADMIN_WORK_ORDER_VIEW)
    public Result<ProWorkOrderVO> getById(@PathVariable Long id) {
        ProWorkOrderVO vo = proWorkOrderService.getProWorkOrderById(id);
        return Result.success(vo);
    }

    //新建工单
    /**
     * JSR303参数分组校验说明
     * 1. Default.class：通用基础校验组，作用于新增、编辑全场景，包含非空、字段长度、数据格式等通用校验规则
     * 2. OnCreate.class：新增专属校验组，仅新增操作生效，例如校验主键id必须为空、新增特有必填字段等规则
     * 校验失败时直接返回错误信息，不会进入后续业务保存逻辑
     */
    @PostMapping
    @RequirePermission(PermissionConstant.ADMIN_WORK_ORDER_MANAGE)
    public Result<ProWorkOrderVO> save(@Validated({Default.class, OnCreate.class}) @RequestBody ProWorkOrderDTO dto) {
        ProWorkOrderVO vo = proWorkOrderService.saveProWorkOrder(dto);
        return Result.success(vo);
    }

    @PutMapping("/{id}")
    @RequirePermission(PermissionConstant.ADMIN_WORK_ORDER_MANAGE)
    public Result<ProWorkOrderVO> update(@PathVariable Long id, @Validated @RequestBody ProWorkOrderDTO dto) {
        ProWorkOrderVO vo = proWorkOrderService.updateProWorkOrder(id, dto);
        return Result.success(vo);
    }

    @DeleteMapping("/{id}")
    @RequirePermission(PermissionConstant.ADMIN_WORK_ORDER_MANAGE)
    public Result<Void> delete(@PathVariable Long id) {
        proWorkOrderService.deleteProWorkOrder(id);
        return Result.success();
    }

    // ========== 执行人指派 ==========

    /**
     * GET /admin/pro-work-order/users/active
     * 获取可用于指派的活跃用户列表。
     */
    @GetMapping("/users/active")
    @RequirePermission(PermissionConstant.ADMIN_WORK_ORDER_ASSIGN)
    public Result<List<SimpleUserVO>> getActiveUsers() {
        List<SimpleUserVO> users = proWorkOrderService.getActiveUsersForAssignment();
        return Result.success(users);
    }

    /**
     * PUT /admin/pro-work-order/{id}/assign
     * 指派执行人。
     */
    @PutMapping("/{id}/assign")
    @RequirePermission(PermissionConstant.ADMIN_WORK_ORDER_ASSIGN)
    @IdempotencyRequired
    public Result<ProWorkOrderVO> assignAssignee(@PathVariable Long id, @RequestParam Long userId) {
        log.info("管理端指派执行人：workOrderId={}, userId={}", id, userId);
        ProWorkOrderVO vo = proWorkOrderService.assignAssignee(id, userId);
        return Result.success(vo);
    }

    /**
     * PUT /admin/pro-work-order/{id}/reassign
     * 更换执行人。
     */
    @PutMapping("/{id}/reassign")
    @RequirePermission(PermissionConstant.ADMIN_WORK_ORDER_ASSIGN)
    @IdempotencyRequired
    public Result<ProWorkOrderVO> reassignAssignee(@PathVariable Long id, @RequestParam Long userId) {
        log.info("管理端更换执行人：workOrderId={}, userId={}", id, userId);
        ProWorkOrderVO vo = proWorkOrderService.reassignAssignee(id, userId);
        return Result.success(vo);
    }

    /**
     * PUT /admin/pro-work-order/{id}/unassign
     * 取消指派。
     */
    @PutMapping("/{id}/unassign")
    @RequirePermission(PermissionConstant.ADMIN_WORK_ORDER_ASSIGN)
    @IdempotencyRequired
    public Result<ProWorkOrderVO> unassignAssignee(@PathVariable Long id) {
        log.info("管理端取消指派执行人：workOrderId={}", id);
        ProWorkOrderVO vo = proWorkOrderService.unassignAssignee(id);
        return Result.success(vo);
    }

    // ==================== 状态机操作 ====================

    /**
     * PUT /admin/pro-work-order/{id}/release
     * 下达工单：CREATED → RELEASED。
     */
    @PutMapping("/{id}/release")
    @RequirePermission(PermissionConstant.ADMIN_WORK_ORDER_RELEASE)
    @IdempotencyRequired
    public Result<ProWorkOrderVO> release(@PathVariable Long id) {
        log.info("管理端下达工单：workOrderId={}", id);
        ProWorkOrderVO vo = proWorkOrderService.releaseWorkOrder(id);
        return Result.success(vo);
    }

    /**
     * PUT /admin/pro-work-order/{id}/pause
     * 暂停工单：IN_PROGRESS → PAUSED。
     */
    @PutMapping("/{id}/pause")
    @RequirePermission(PermissionConstant.ADMIN_WORK_ORDER_PAUSE)
    @IdempotencyRequired
    public Result<ProWorkOrderVO> pause(@PathVariable Long id) {
        log.info("管理端暂停工单：workOrderId={}", id);
        ProWorkOrderVO vo = proWorkOrderService.pauseWorkOrder(id);
        return Result.success(vo);
    }

    /**
     * PUT /admin/pro-work-order/{id}/resume
     * 恢复工单：PAUSED → IN_PROGRESS。
     */
    @PutMapping("/{id}/resume")
    @RequirePermission(PermissionConstant.ADMIN_WORK_ORDER_RESUME)
    @IdempotencyRequired
    public Result<ProWorkOrderVO> resume(@PathVariable Long id) {
        log.info("管理端恢复工单：workOrderId={}", id);
        ProWorkOrderVO vo = proWorkOrderService.resumeWorkOrder(id);
        return Result.success(vo);
    }

    /**
     * PUT /admin/pro-work-order/{id}/close
     * 关闭工单：COMPLETED → CLOSED。
     */
    @PutMapping("/{id}/close")
    @RequirePermission(PermissionConstant.ADMIN_WORK_ORDER_CLOSE)
    @IdempotencyRequired
    public Result<ProWorkOrderVO> close(@PathVariable Long id) {
        log.info("管理端关闭工单：workOrderId={}", id);
        ProWorkOrderVO vo = proWorkOrderService.closeWorkOrder(id);
        return Result.success(vo);
    }

    /**
     * PUT /admin/pro-work-order/{id}/cancel
     * 取消工单：CREATED 或 RELEASED → CANCELLED。
     */
    @PutMapping("/{id}/cancel")
    @RequirePermission(PermissionConstant.ADMIN_WORK_ORDER_CANCEL)
    @IdempotencyRequired
    public Result<ProWorkOrderVO> cancel(@PathVariable Long id,
                                         @RequestParam(required = false) String remark) {
        log.info("管理端取消工单：workOrderId={}, remark={}", id, remark);
        ProWorkOrderVO vo = proWorkOrderService.cancelWorkOrder(id, remark);
        return Result.success(vo);
    }
}
