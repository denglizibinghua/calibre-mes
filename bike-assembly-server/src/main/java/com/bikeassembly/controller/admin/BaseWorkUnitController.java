package com.bikeassembly.controller.admin;

import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.PermissionConstant;

import com.bikeassembly.dto.BaseWorkUnitDTO;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.BaseWorkUnitService;
import com.bikeassembly.vo.BaseWorkUnitVO;

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
@RequestMapping("/admin/base-work-unit")
@Slf4j
public class BaseWorkUnitController {

    @Autowired
    private BaseWorkUnitService baseWorkUnitService;

    @RequirePermission(PermissionConstant.ADMIN_WORK_UNIT_VIEW)
    @GetMapping("/list")
    public Result<PageResult<BaseWorkUnitVO>> list(
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "15") Long size) {
        PageResult<BaseWorkUnitVO> pageResult = baseWorkUnitService.listWorkUnits(page, size);
        return Result.success(pageResult);
    }

    @RequirePermission(PermissionConstant.ADMIN_WORK_UNIT_VIEW)
    @GetMapping("/{id}")
    public Result<BaseWorkUnitVO> getById(@PathVariable Long id) {
        BaseWorkUnitVO vo = baseWorkUnitService.getWorkUnitById(id);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_WORK_UNIT_MANAGE)
    @PostMapping
    public Result<BaseWorkUnitVO> save(@Validated({Default.class, OnCreate.class}) @RequestBody BaseWorkUnitDTO dto) {
        BaseWorkUnitVO vo = baseWorkUnitService.saveWorkUnit(dto);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_WORK_UNIT_MANAGE)
    @PutMapping("/{id}")
    public Result<BaseWorkUnitVO> update(@PathVariable Long id, @Validated @RequestBody BaseWorkUnitDTO dto) {
        BaseWorkUnitVO vo = baseWorkUnitService.updateWorkUnit(id, dto);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_WORK_UNIT_MANAGE)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        baseWorkUnitService.deleteWorkUnit(id);
        return Result.success();
    }
}
