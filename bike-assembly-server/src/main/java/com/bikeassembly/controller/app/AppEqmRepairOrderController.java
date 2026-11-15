package com.bikeassembly.controller.app;

import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.constant.PermissionConstant;
import com.bikeassembly.context.BaseContext;
import com.bikeassembly.dto.CompleteRepairDTO;
import com.bikeassembly.dto.EqmRepairOrderDTO;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.exception.ForbiddenException;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.EqmRepairOrderService;
import com.bikeassembly.service.PermissionService;
import com.bikeassembly.util.UserContextHelper;
import com.bikeassembly.utils.ScopeValidator;
import com.bikeassembly.vo.EqmRepairOrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 小程序端设备报修接口 — 查询 + 报修 + 处理，支持 scope=mine|all + relation=reported|handled|pending。
 *
 * 状态机：0=待受理 → 1=维修中 → 2=已完成
 */
@RestController
@RequestMapping("/app/eqm-repair-order")
@Slf4j
public class AppEqmRepairOrderController {

    @Autowired
    private EqmRepairOrderService eqmRepairOrderService;
    @Autowired
    private PermissionService permissionService;
    @Autowired
    private UserContextHelper userContextHelper;

    /**
     * GET /app/eqm-repair-order/list
     * 获取维修单列表。
     */
    @GetMapping("/list")
    @RequirePermission(PermissionConstant.APP_REPAIR_VIEW)
    public Result<List<EqmRepairOrderVO>> list(@RequestParam(defaultValue = "mine") String scope,
                                                @RequestParam(required = false) String relation,
                                                @RequestParam(required = false) Long equipmentId) {
        // Controller 层严格校验 scope 合法值，拒绝未知值、空字符串、空格、大小写变体
        scope = ScopeValidator.requireValid(scope, ScopeValidator.MINE_OR_ALL);
        Long userId = BaseContext.getCurrentId();

        if ("all".equals(scope)) {
            if (!permissionService.hasPermission(userId, PermissionConstant.APP_REPAIR_VIEW_ALL)) {
                throw new ForbiddenException(MessageConstant.PERMISSION_DENIED);
            }
        }

        // pending 需要独立权限（relation 参数，不是 scope）
        if ("pending".equals(relation)) {
            if (!permissionService.hasPermission(userId, PermissionConstant.APP_REPAIR_VIEW_PENDING)) {
                throw new ForbiddenException(MessageConstant.PERMISSION_DENIED);
            }
        }

        PageResult<EqmRepairOrderVO> pageResult = eqmRepairOrderService.listEqmRepairOrders(1L, 9999L, scope, relation);
        List<EqmRepairOrderVO> list = pageResult.getRecords();
        if (equipmentId != null) {
            list = list.stream().filter(v -> equipmentId.equals(v.getEquipmentId())).collect(Collectors.toList());
        }
        return Result.success(list);
    }

    /**
     * GET /app/eqm-repair-order/{id}
     * 获取单条维修单详情。
     * 归属校验：报修人、维修处理人 或 拥有 view-all 权限的用户可查看。
     */
    @GetMapping("/{id}")
    @RequirePermission(PermissionConstant.APP_REPAIR_VIEW)
    public Result<EqmRepairOrderVO> getById(@PathVariable Long id) {
        EqmRepairOrderVO vo = eqmRepairOrderService.getEqmRepairOrderById(id);
        // App 归属校验：报修人、处理人 或 app:repair:view-all
        Long currentUserId = BaseContext.getCurrentId();
        boolean isReporter = vo.getReporterUserId() != null && vo.getReporterUserId().equals(currentUserId);
        boolean isHandler = vo.getHandlerUserId() != null && vo.getHandlerUserId().equals(currentUserId);
        boolean hasViewAll = permissionService.hasPermission(currentUserId, PermissionConstant.APP_REPAIR_VIEW_ALL);
        if (!isReporter && !isHandler && !hasViewAll) {
            throw new ForbiddenException(MessageConstant.PERMISSION_DENIED);
        }
        return Result.success(vo);
    }

    /**
     * POST /app/eqm-repair-order
     * 提交设备报修。报修人由后端自动填充。
     */
    @PostMapping
    @RequirePermission(PermissionConstant.APP_REPAIR_CREATE)
    public Result<EqmRepairOrderVO> create(@Validated @RequestBody EqmRepairOrderDTO dto) {
        // 禁止前端传入 user_id 字段
        dto.setReporterUserId(null);
        dto.setHandlerUserId(null);

        if (dto.getEquipmentId() == null) {
            throw new BaseException("设备不能为空");
        }
        if (dto.getFaultDesc() == null || dto.getFaultDesc().isBlank()) {
            throw new BaseException("故障描述不能为空");
        }
        dto.setReportUser(userContextHelper.getCurrentUserDisplayName());
        dto.setReporterUserId(userContextHelper.getCurrentUserId());
        if (dto.getReportTime() == null) {
            dto.setReportTime(LocalDateTime.now());
        }
        log.info("小程序提交设备报修：equipmentId={}, faultDesc={}, reportUser={}",
                dto.getEquipmentId(), dto.getFaultDesc(), dto.getReportUser());
        EqmRepairOrderVO vo = eqmRepairOrderService.saveEqmRepairOrder(dto);
        return Result.success(vo);
    }

    /**
     * PUT /app/eqm-repair-order/{id}/accept
     * 受理维修：将待受理状态变为维修中。
     * 维修人由后端自动填充为当前用户。
     */
    @PutMapping("/{id}/accept")
    @RequirePermission(PermissionConstant.APP_REPAIR_ACCEPT)
    public Result<EqmRepairOrderVO> accept(@PathVariable Long id) {
        log.info("小程序受理维修：repairId={}, userId={}", id, BaseContext.getCurrentId());
        EqmRepairOrderVO vo = eqmRepairOrderService.acceptEqmRepairOrder(id);
        return Result.success(vo);
    }

    /**
     * PUT /app/eqm-repair-order/{id}/complete
     * 完成维修：将维修中状态变为已完成。接收维修结果DTO。
     */
    @PutMapping("/{id}/complete")
    @RequirePermission(PermissionConstant.APP_REPAIR_COMPLETE)
    public Result<EqmRepairOrderVO> complete(@PathVariable Long id,
                                              @Validated @RequestBody(required = false) CompleteRepairDTO dto) {
        log.info("小程序完成维修：repairId={}, userId={}", id, BaseContext.getCurrentId());
        EqmRepairOrderVO vo = eqmRepairOrderService.completeEqmRepairOrderWithDto(id, dto);
        return Result.success(vo);
    }
}
