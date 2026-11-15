package com.bikeassembly.controller.admin;

import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.PermissionConstant;

import com.bikeassembly.dto.ErpSyncLogDTO;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.ErpSyncLogService;
import com.bikeassembly.validation.OnCreate;
import com.bikeassembly.vo.ErpSyncLogVO;
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

@RestController
@RequestMapping("/admin/erp-sync-log")
@Slf4j
public class ErpSyncLogController {

    @Autowired
    private ErpSyncLogService erpSyncLogService;

    @RequirePermission(PermissionConstant.ADMIN_ERP_SYNC_LOG_VIEW)
    @GetMapping("/list")
    public Result<PageResult<ErpSyncLogVO>> list(@RequestParam(defaultValue = "1") Long page,
                                                    @RequestParam(defaultValue = "15") Long size) {
        PageResult<ErpSyncLogVO> pageResult = erpSyncLogService.listSyncLogs(page, size);
        return Result.success(pageResult);
    }

    @RequirePermission(PermissionConstant.ADMIN_ERP_SYNC_LOG_VIEW)
    @GetMapping("/{id}")
    public Result<ErpSyncLogVO> getById(@PathVariable Long id) {
        ErpSyncLogVO vo = erpSyncLogService.getSyncLogById(id);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_ERP_SYNC_LOG_MANAGE)
    @PostMapping
    public Result<ErpSyncLogVO> save(@Validated({Default.class, OnCreate.class}) @RequestBody ErpSyncLogDTO dto) {
        ErpSyncLogVO vo = erpSyncLogService.saveSyncLog(dto);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_ERP_SYNC_LOG_MANAGE)
    @PutMapping("/{id}")
    public Result<ErpSyncLogVO> update(@PathVariable Long id, @Validated @RequestBody ErpSyncLogDTO dto) {
        ErpSyncLogVO vo = erpSyncLogService.updateSyncLog(id, dto);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_ERP_SYNC_LOG_MANAGE)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        erpSyncLogService.deleteSyncLog(id);
        return Result.success();
    }

    /**
     * 重试失败的 ERP 同步（会重新读取业务快照并调用 ERP）。
     */
    @RequirePermission(PermissionConstant.ADMIN_ERP_SYNC_LOG_MANAGE)
    @PostMapping("/{id}/retry")
    public Result<ErpSyncLogVO> retry(@PathVariable Long id) {
        ErpSyncLogVO vo = erpSyncLogService.retrySync(id);
        return Result.success(vo);
    }
}
