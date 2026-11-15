package com.bikeassembly.controller.app;

import com.bikeassembly.annotation.IdempotencyRequired;
import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.constant.PermissionConstant;
import com.bikeassembly.context.BaseContext;
import com.bikeassembly.dto.ProWorkProcessDTO;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.exception.ForbiddenException;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.PermissionService;
import com.bikeassembly.service.ProWorkProcessService;
import com.bikeassembly.util.UserContextHelper;
import com.bikeassembly.utils.ScopeValidator;
import com.bikeassembly.validation.OnCreate;
import com.bikeassembly.vo.ProWorkProcessVO;
import jakarta.validation.groups.Default;
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

import java.math.BigDecimal;
import java.util.List;

/**
 * 小程序端工序流转接口 — 查询 + 报工 + 返工。
 */
@RestController
@RequestMapping("/app/pro-work-process")
@Slf4j
public class AppProWorkProcessController {

    @Autowired
    private ProWorkProcessService proWorkProcessService;
    @Autowired
    private PermissionService permissionService;
    @Autowired
    private UserContextHelper userContextHelper;

    /**
     * GET /app/pro-work-process/list
     */
    @GetMapping("/list")
    @RequirePermission(PermissionConstant.APP_WORK_PROCESS_VIEW)
    public Result<List<ProWorkProcessVO>> list(@RequestParam(defaultValue = "mine") String scope,
                                               @RequestParam(required = false) Long workOrderId) {
        return Result.success(proWorkProcessService.listAppProWorkProcesses(scope, workOrderId).getRecords());
    }

    /**
     * GET /app/pro-work-process/{id}
     * 含归属校验：非 view-all 用户只能查看自己工单的工序。
     */
    @GetMapping("/{id}")
    @RequirePermission(PermissionConstant.APP_WORK_PROCESS_VIEW)
    public Result<ProWorkProcessVO> getById(@PathVariable Long id) {
        ProWorkProcessVO vo = proWorkProcessService.getProWorkProcessById(id);
        // 归属校验：检查工序所属工单是否指派给当前用户
        if (!permissionService.hasPermission(BaseContext.getCurrentId(),
                PermissionConstant.APP_WORK_PROCESS_VIEW_ALL)) {
            proWorkProcessService.validateProcessOwnershipForRead(id);
        }
        return Result.success(vo);
    }

    /**
     * POST /app/pro-work-process
     * 新建工序报工记录。
     */
    @PostMapping
    @RequirePermission(PermissionConstant.APP_WORK_PROCESS_CREATE)
    public Result<ProWorkProcessVO> create(@Validated({Default.class, OnCreate.class}) @RequestBody ProWorkProcessDTO dto) {
        throw new BaseException("生产工序由工单下达自动生成，不支持手工创建");
    }

    /**
     * PUT /app/pro-work-process/{id}/start
     * 开始工序：READY → IN_PROGRESS。
     */
    @PutMapping("/{id}/start")
    @RequirePermission(PermissionConstant.APP_WORK_PROCESS_START)
    @IdempotencyRequired
    public Result<ProWorkProcessVO> start(@PathVariable Long id) {
        log.info("小程序开始工序：processRecordId={}, userId={}", id, BaseContext.getCurrentId());
        ProWorkProcessVO vo = proWorkProcessService.startProWorkProcess(id);
        return Result.success(vo);
    }

    // ==================== 状态机新端点 ====================

    /**
     * PUT /app/pro-work-process/{id}/report
     * 统一报工入口：后端自动判断是否需要质检。
     * - 无需质检工序：IN_PROGRESS → COMPLETED
     * - 需质检工序：IN_PROGRESS → WAIT_INSPECTION + 自动创建质检单
     * 请求参数：reportQty（必填），qcResultNote（选填）
     */
    @PutMapping("/{id}/report")
    @RequirePermission(PermissionConstant.APP_WORK_PROCESS_REPORT)
    @IdempotencyRequired
    public Result<ProWorkProcessVO> report(@PathVariable Long id,
                                            @RequestParam BigDecimal reportQty,
                                            @RequestParam(required = false) String qcResultNote) {
        log.info("小程序统一报工：processRecordId={}, reportQty={}", id, reportQty);
        ProWorkProcessVO vo = proWorkProcessService.unifiedReport(id, reportQty, qcResultNote);
        return Result.success(vo);
    }

    /**
     * PUT /app/pro-work-process/{id}/report-complete
     * 报工完成（无需质检）：IN_PROGRESS → COMPLETED。
     */
    @PutMapping("/{id}/report-complete")
    @RequirePermission(PermissionConstant.APP_WORK_PROCESS_REPORT)
    public Result<ProWorkProcessVO> reportComplete(@PathVariable Long id,
                                                    @RequestParam BigDecimal reportQty) {
        throw new com.bikeassembly.exception.BaseException("该接口已废弃，请使用统一报工接口 PUT /app/pro-work-process/{id}/report");
    }

    /**
     * PUT /app/pro-work-process/{id}/rework-start
     * 操作员发起返工：REJECTED → REWORK。要求当前用户必须是工单执行人。
     */
    @PutMapping("/{id}/rework-start")
    @RequirePermission(PermissionConstant.APP_WORK_PROCESS_REWORK_START)
    @IdempotencyRequired
    public Result<ProWorkProcessVO> reworkStart(@PathVariable Long id) {
        log.info("小程序操作员发起返工：processRecordId={}, userId={}", id, BaseContext.getCurrentId());
        ProWorkProcessVO vo = proWorkProcessService.operatorReworkStart(id);
        return Result.success(vo);
    }

    /**
     * PUT /app/pro-work-process/{id}/rework-complete
     * 操作员返工完成：REWORK → WAIT_INSPECTION（必须复检）。
     * 要求当前用户必须是工单执行人。不累加报工数量。
     */
    @PutMapping("/{id}/rework-complete")
    @RequirePermission(PermissionConstant.APP_WORK_PROCESS_REWORK_COMPLETE)
    @IdempotencyRequired
    public Result<ProWorkProcessVO> reworkComplete(@PathVariable Long id) {
        log.info("小程序操作员返工完成：processRecordId={}, userId={}", id, BaseContext.getCurrentId());
        ProWorkProcessVO vo = proWorkProcessService.operatorReworkComplete(id);
        return Result.success(vo);
    }
}
