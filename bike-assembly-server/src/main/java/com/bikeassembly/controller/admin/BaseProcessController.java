package com.bikeassembly.controller.admin;

import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.PermissionConstant;

import com.bikeassembly.dto.BaseProcessDTO;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.BaseProcessService;
import com.bikeassembly.vo.BaseProcessVO;

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
@RequestMapping("/admin/base-process")
@Slf4j
public class BaseProcessController {

    @Autowired
    private BaseProcessService baseProcessService;

    @RequirePermission(PermissionConstant.ADMIN_PROCESS_VIEW)
    @GetMapping("/list")
    public Result<PageResult<BaseProcessVO>> list(
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "15") Long size) {
        PageResult<BaseProcessVO> pageResult = baseProcessService.listProcesses(page, size);
        return Result.success(pageResult);
    }

    @RequirePermission(PermissionConstant.ADMIN_PROCESS_VIEW)
    @GetMapping("/{id}")
    public Result<BaseProcessVO> getById(@PathVariable Long id) {
        BaseProcessVO vo = baseProcessService.getProcessById(id);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_PROCESS_MANAGE)
    @PostMapping
    public Result<BaseProcessVO> save(@Validated({Default.class, OnCreate.class}) @RequestBody BaseProcessDTO dto) {
        BaseProcessVO vo = baseProcessService.saveProcess(dto);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_PROCESS_MANAGE)
    @PutMapping("/{id}")
    public Result<BaseProcessVO> update(@PathVariable Long id, @Validated @RequestBody BaseProcessDTO dto) {
        BaseProcessVO vo = baseProcessService.updateProcess(id, dto);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_PROCESS_MANAGE)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        baseProcessService.deleteProcess(id);
        return Result.success();
    }
}
