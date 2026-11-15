package com.bikeassembly.controller.admin;

import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.PermissionConstant;

import com.bikeassembly.dto.SysOperLogDTO;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.SysOperLogService;
import com.bikeassembly.vo.SysOperLogVO;

import com.bikeassembly.validation.OnCreate;
import jakarta.validation.groups.Default;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.beans.factory.annotation.Autowired;
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
@RequestMapping("/admin/sys-oper-log")
@Slf4j
public class SysOperLogController {

    @Autowired
    private SysOperLogService sysOperLogService;

    @RequirePermission(PermissionConstant.ADMIN_OPER_LOG_VIEW)
    @GetMapping("/list")
    public Result<PageResult<SysOperLogVO>> list(@RequestParam(defaultValue = "1") Long page,
                                                  @RequestParam(defaultValue = "15") Long size) {
        PageResult<SysOperLogVO> result = sysOperLogService.listOperLogs(page, size);
        return Result.success(result);
    }

    @RequirePermission(PermissionConstant.ADMIN_OPER_LOG_VIEW)
    @GetMapping("/{id}")
    public Result<SysOperLogVO> getById(@PathVariable Long id) {
        SysOperLogVO vo = sysOperLogService.getOperLogById(id);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_OPER_LOG_MANAGE)
    @PostMapping
    public Result<SysOperLogVO> save(@Validated({Default.class, OnCreate.class}) @RequestBody SysOperLogDTO dto) {
        SysOperLogVO vo = sysOperLogService.saveOperLog(dto);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_OPER_LOG_MANAGE)
    @PutMapping("/{id}")
    public Result<SysOperLogVO> update(@PathVariable Long id, @Validated @RequestBody SysOperLogDTO dto) {
        SysOperLogVO vo = sysOperLogService.updateOperLog(id, dto);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_OPER_LOG_MANAGE)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysOperLogService.deleteOperLog(id);
        return Result.success();
    }
}
