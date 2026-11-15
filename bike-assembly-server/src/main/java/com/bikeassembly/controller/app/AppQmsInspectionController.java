package com.bikeassembly.controller.app;

import com.bikeassembly.annotation.IdempotencyRequired;
import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.constant.PermissionConstant;
import com.bikeassembly.context.BaseContext;
import com.bikeassembly.dto.QmsInspectionDTO;
import com.bikeassembly.exception.ForbiddenException;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.PermissionService;
import com.bikeassembly.service.QmsInspectionService;
import com.bikeassembly.util.UserContextHelper;
import com.bikeassembly.utils.ScopeValidator;
import com.bikeassembly.vo.QmsInspectionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 小程序端质检单接口 — 查询 + 录入 + 确认，支持 scope=mine|all。
 *
 * 状态机：0=待检 → 1=已录入 → 2=已确认
 */
@RestController
@RequestMapping("/app/qms-inspection")
@Slf4j
public class AppQmsInspectionController {

    @Autowired
    private QmsInspectionService qmsInspectionService;
    @Autowired
    private PermissionService permissionService;
    @Autowired
    private UserContextHelper userContextHelper;

    /**
     * GET /app/qms-inspection/list
     *
     * scope:
     *   mine    — inspector_user_id = 当前用户（我录入/确认的任务）
     *   pending — status=0 AND inspector_user_id IS NULL（待领取，需 view-pending 权限）
     *   all     — 全部（需 view-all 权限）
     */
    @GetMapping("/list")
    @RequirePermission(PermissionConstant.APP_INSPECTION_VIEW)
    public Result<List<QmsInspectionVO>> list(@RequestParam(defaultValue = "mine") String scope,
                                               @RequestParam(required = false) Long workOrderId,
                                               @RequestParam(required = false) Long processId,
                                               @RequestParam(required = false) String status) {
        return Result.success(qmsInspectionService.listAppQmsInspections(scope, workOrderId, processId, status).getRecords());
    }

    /**
     * GET /app/qms-inspection/{id}
     * 获取单条质检单详情。非 view-all 用户只能查看关联自己工单的质检单。
     */
    @GetMapping("/{id}")
    @RequirePermission(PermissionConstant.APP_INSPECTION_VIEW)
    public Result<QmsInspectionVO> getById(@PathVariable Long id) {
        QmsInspectionVO vo = qmsInspectionService.getQmsInspectionById(id);
        // 归属校验：非 view-all 质检员只能查看关联自己工单的质检单
        if (!permissionService.hasPermission(BaseContext.getCurrentId(),
                PermissionConstant.APP_INSPECTION_VIEW_ALL)) {
            qmsInspectionService.validateInspectionOwnershipForRead(id);
        }
        return Result.success(vo);
    }

    /**
     * PUT /app/qms-inspection/{id}/result
     * 录入检验结果：更新已有待检质检单，status: 0 → 1。
     * 检验人由后端根据当前登录用户自动填充。
     */
    @PutMapping("/{id}/result")
    @RequirePermission(PermissionConstant.APP_INSPECTION_CREATE)
    @IdempotencyRequired
    public Result<QmsInspectionVO> enterResult(@PathVariable Long id,
                                               @Validated @RequestBody QmsInspectionDTO dto) {
        dto.setInspectorUserId(null);
        String inspectorName = userContextHelper.getCurrentUserDisplayName();
        dto.setInspector(inspectorName);
        dto.setInspectorUserId(userContextHelper.getCurrentUserId());
        if (dto.getInspectionTime() == null) {
            dto.setInspectionTime(LocalDateTime.now());
        }
        log.info("小程序录入检验结果：inspectionId={}, result={}, inspector={}", id, dto.getResult(), inspectorName);
        QmsInspectionVO vo = qmsInspectionService.enterInspectionResult(id, dto);
        return Result.success(vo);
    }

    /**
     * PUT /app/qms-inspection/{id}/confirm
     * 确认质检：将已录入状态变为已确认。
     */
    @PutMapping("/{id}/confirm")
    @RequirePermission(PermissionConstant.APP_INSPECTION_CONFIRM)
    @IdempotencyRequired
    public Result<QmsInspectionVO> confirm(@PathVariable Long id) {
        log.info("小程序确认质检：inspectionId={}, userId={}", id, BaseContext.getCurrentId());
        QmsInspectionVO vo = qmsInspectionService.confirmInspection(id);
        return Result.success(vo);
    }
}
