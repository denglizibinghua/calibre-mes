package com.bikeassembly.controller.admin;

import com.bikeassembly.annotation.IdempotencyRequired;
import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.PermissionConstant;
import com.bikeassembly.dto.ProWorkProcessDTO;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.ProWorkProcessService;
import com.bikeassembly.validation.OnCreate;
import com.bikeassembly.vo.ProWorkProcessVO;
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

import java.math.BigDecimal;

@RestController
@RequestMapping("/admin/pro-work-process")
@Slf4j
public class ProWorkProcessController {

    @Autowired
    private ProWorkProcessService proWorkProcessService;

    @GetMapping("/list")
    @RequirePermission(PermissionConstant.ADMIN_WORK_PROCESS_VIEW)
    public Result<PageResult<ProWorkProcessVO>> list(@RequestParam(defaultValue = "1") Long page,
                                                      @RequestParam(defaultValue = "15") Long size) {
        PageResult<ProWorkProcessVO> pageResult = proWorkProcessService.listProWorkProcesses(page, size, "all");
        return Result.success(pageResult);
    }

    @GetMapping("/{id}")
    @RequirePermission(PermissionConstant.ADMIN_WORK_PROCESS_VIEW)
    public Result<ProWorkProcessVO> getById(@PathVariable Long id) {
        ProWorkProcessVO vo = proWorkProcessService.getProWorkProcessById(id);
        return Result.success(vo);
    }

    @PostMapping
    @RequirePermission(PermissionConstant.ADMIN_WORK_PROCESS_MANAGE)
    public Result<ProWorkProcessVO> save(@Validated({Default.class, OnCreate.class}) @RequestBody ProWorkProcessDTO dto) {
        throw new com.bikeassembly.exception.BaseException("生产工序由工单下达自动生成，不支持手工创建");
    }

    /**
     * PUT /admin/pro-work-process/{id}
     * 管理端工序编辑：仅允许修改设备绑定（equipmentId），其他受保护字段（工单、工序、状态、
     * 报工数量、操作员、质检结果、时间等）均不可修改。version 用于乐观锁校验。
     */
    @PutMapping("/{id}")
    @RequirePermission(PermissionConstant.ADMIN_WORK_PROCESS_MANAGE)
    public Result<ProWorkProcessVO> update(@PathVariable Long id,
                                            @Validated @RequestBody com.bikeassembly.dto.ProWorkProcessUpdateDTO dto) {
        // 将专用 DTO 转为通用 DTO 传给 Service（Service 只处理允许的字段）
        ProWorkProcessDTO legacyDto = new ProWorkProcessDTO();
        legacyDto.setEquipmentId(dto.getEquipmentId());
        legacyDto.setVersion(dto.getVersion());
        ProWorkProcessVO vo = proWorkProcessService.updateProWorkProcess(id, legacyDto);
        return Result.success(vo);
    }

    @DeleteMapping("/{id}")
    @RequirePermission(PermissionConstant.ADMIN_WORK_PROCESS_MANAGE)
    public Result<Void> delete(@PathVariable Long id) {
        proWorkProcessService.deleteProWorkProcess(id);
        return Result.success();
    }

    // ==================== 状态机操作 ====================

    /**
     * PUT /admin/pro-work-process/{id}/qc-pass
     * [已废弃] 质检通过——禁止直接通过工序接口修改质检结果。
     * 所有质检判定必须通过质检单确认流程：
     * 小程序端 AppQmsInspectionController.enterResult → confirm → 联动工序状态。
     */
    @PutMapping("/{id}/qc-pass")
    @RequirePermission(PermissionConstant.ADMIN_WORK_PROCESS_QC_PASS)
    @Deprecated
    public Result<ProWorkProcessVO> qcPass(@PathVariable Long id,
                                           @RequestParam(required = false) String qcNote) {
        log.warn("管理端尝试直接调用已废弃的质检通过接口：processRecordId={}", id);
        throw new com.bikeassembly.exception.BaseException(
                "该接口已废弃。质检结果必须通过质检单确认，请在小程序端质量检验页面操作。"
                + "路径：/app/qms-inspection → 找到工序关联的质检单 → 录入结果 → 确认质检。");
    }

    /**
     * PUT /admin/pro-work-process/{id}/qc-fail
     * [已废弃] 质检驳回——禁止直接通过工序接口修改质检结果。
     * 所有质检判定必须通过质检单确认流程。
     */
    @PutMapping("/{id}/qc-fail")
    @RequirePermission(PermissionConstant.ADMIN_WORK_PROCESS_QC_FAIL)
    @Deprecated
    public Result<ProWorkProcessVO> qcFail(@PathVariable Long id,
                                           @RequestParam(required = false) String qcNote) {
        log.warn("管理端尝试直接调用已废弃的质检驳回接口：processRecordId={}", id);
        throw new com.bikeassembly.exception.BaseException(
                "该接口已废弃。质检结果必须通过质检单确认，请在小程序端质量检验页面操作。"
                + "路径：/app/qms-inspection → 找到工序关联的质检单 → 录入结果 → 确认质检。");
    }

    /**
     * PUT /admin/pro-work-process/{id}/rework-start
     * 管理端强制返工：不校验工单执行人归属，需 admin:work-process:rework 权限。
     * 必须填写返工原因。Service 层再次校验权限，是最终安全边界。
     */
    @PutMapping("/{id}/rework-start")
    @RequirePermission(PermissionConstant.ADMIN_WORK_PROCESS_REWORK)
    @IdempotencyRequired
    public Result<ProWorkProcessVO> reworkStart(@PathVariable Long id,
                                                 @RequestParam String reason) {
        log.info("管理端强制发起返工：processRecordId={}, reason={}, userId={}",
                id, reason, com.bikeassembly.context.BaseContext.getCurrentId());
        ProWorkProcessVO vo = proWorkProcessService.adminReworkStart(id, reason);
        return Result.success(vo);
    }

    /**
     * PUT /admin/pro-work-process/{id}/rework-complete
     * 管理端强制返工完成：不校验执行人归属，需 admin:work-process:rework 权限。
     * remark 为可选备注（非报工数量），Service 层再次校验权限。
     */
    @PutMapping("/{id}/rework-complete")
    @RequirePermission(PermissionConstant.ADMIN_WORK_PROCESS_REWORK)
    @IdempotencyRequired
    public Result<ProWorkProcessVO> reworkComplete(@PathVariable Long id,
                                                    @RequestParam(required = false) String remark) {
        log.info("管理端强制返工完成：processRecordId={}, remark={}, userId={}",
                id, remark, com.bikeassembly.context.BaseContext.getCurrentId());
        ProWorkProcessVO vo = proWorkProcessService.adminReworkComplete(id, remark);
        return Result.success(vo);
    }

    /**
     * PUT /admin/pro-work-process/{id}/skip
     * 跳过工序。
     */
    @PutMapping("/{id}/skip")
    @RequirePermission(PermissionConstant.ADMIN_WORK_PROCESS_SKIP)
    @IdempotencyRequired
    public Result<ProWorkProcessVO> skip(@PathVariable Long id,
                                         @RequestParam(required = false) String remark) {
        log.info("管理端跳过工序：processRecordId={}, remark={}", id, remark);
        ProWorkProcessVO vo = proWorkProcessService.skipProcess(id, remark);
        return Result.success(vo);
    }

    /**
     * PUT /admin/pro-work-process/{id}/cancel
     * 取消工序。
     */
    @PutMapping("/{id}/cancel")
    @RequirePermission(PermissionConstant.ADMIN_WORK_PROCESS_CANCEL)
    @IdempotencyRequired
    public Result<ProWorkProcessVO> cancel(@PathVariable Long id,
                                            @RequestParam(required = false) String remark) {
        log.info("管理端取消工序：processRecordId={}, remark={}", id, remark);
        ProWorkProcessVO vo = proWorkProcessService.cancelProcess(id, remark);
        return Result.success(vo);
    }
}
