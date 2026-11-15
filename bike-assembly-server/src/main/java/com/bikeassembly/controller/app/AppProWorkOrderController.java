package com.bikeassembly.controller.app;

import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.PermissionConstant;
import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.result.Result;
import com.bikeassembly.exception.ForbiddenException;
import com.bikeassembly.service.PermissionService;
import com.bikeassembly.service.ProWorkOrderService;
import com.bikeassembly.utils.ScopeValidator;
import com.bikeassembly.vo.ProWorkOrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小程序端工单接口 — 只读查询，支持 scope=mine|all。
 */
@RestController
@RequestMapping("/app/pro-work-order")
@Slf4j
public class AppProWorkOrderController {

    @Autowired
    private ProWorkOrderService proWorkOrderService;
    @Autowired
    private PermissionService permissionService;

    /**
     * [答辩入口] 小程序端工单列表查询。
     * 完整路径：GET /app/pro-work-order/list?scope=mine&orderNo=&productName=
     * [请求参数] scope（默认 "mine"，仅返回当前用户被指派的工单）、orderNo/productName（可选模糊查询）
     * [调用链] Controller → ProWorkOrderService.listProWorkOrders(page=1, size=9999) → ProWorkOrderMapper.selectPageByCondition
     * [权限控制] @RequirePermission(APP_WORK_ORDER_VIEW)，scope="all" 时额外检查 APP_WORK_ORDER_VIEW_ALL 权限
     * [前端调用] 小程序 work-order/index.vue 的 fetchData() → getWorkOrderListApi() → GET /app/pro-work-order/list
     * [实现情况] 小程序端不传分页参数，固定 page=1 size=9999 一次性返回全部工单（前端自己做状态筛选和搜索过滤）
     */
    @GetMapping("/list")
    @RequirePermission(PermissionConstant.APP_WORK_ORDER_VIEW)
    public Result<List<ProWorkOrderVO>> list(@RequestParam(defaultValue = "mine") String scope,
                                              @RequestParam(required = false) String workOrderNo,
                                              @RequestParam(required = false) String orderNo,
                                              @RequestParam(required = false) String productName) {
        // Controller 层严格校验 scope 合法值，拒绝未知值、空字符串、空格、大小写变体
        scope = ScopeValidator.requireValid(scope, ScopeValidator.MINE_OR_ALL);
        if ("all".equals(scope)) {
            if (!permissionService.hasPermission(
                    com.bikeassembly.context.BaseContext.getCurrentId(),
                    PermissionConstant.APP_WORK_ORDER_VIEW_ALL)) {
                throw new ForbiddenException(MessageConstant.PERMISSION_DENIED);
            }
        }
        PageResult<ProWorkOrderVO> pageResult = proWorkOrderService.listProWorkOrders(
                1L, 9999L, workOrderNo, orderNo, productName, scope);
        return Result.success(pageResult.getRecords());
    }

    /**
     * 获取单条工单详情（含归属校验）。
     * GET /app/pro-work-order/{id}
     * 无 view-all 权限时：只能查看自己的工单（assignee_user_id = 当前用户）
     */
    @GetMapping("/{id}")
    @RequirePermission(PermissionConstant.APP_WORK_ORDER_VIEW)
    public Result<ProWorkOrderVO> getById(@PathVariable Long id) {
        ProWorkOrderVO vo = proWorkOrderService.getProWorkOrderById(id);
        // 归属校验：非 view-all 用户只能查看自己的工单
        if (!permissionService.hasPermission(
                com.bikeassembly.context.BaseContext.getCurrentId(),
                PermissionConstant.APP_WORK_ORDER_VIEW_ALL)) {
            Long currentUserId = com.bikeassembly.context.BaseContext.getCurrentId();
            if (vo.getAssigneeUserId() == null || !vo.getAssigneeUserId().equals(currentUserId)) {
                throw new ForbiddenException(MessageConstant.PERMISSION_DENIED);
            }
        }
        return Result.success(vo);
    }
}
