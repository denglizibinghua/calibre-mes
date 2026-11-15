package com.bikeassembly.controller.admin;

import com.bikeassembly.annotation.RequirePermission;

import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.PermissionConstant;
import com.bikeassembly.dto.EqmMaintenanceDTO;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.EqmMaintenanceService;
import com.bikeassembly.validation.OnCreate;
import com.bikeassembly.vo.EqmMaintenanceVO;
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
@RequestMapping("/admin/eqm-maintenance")
@Slf4j
public class EqmMaintenanceController {

    @Autowired
    private EqmMaintenanceService eqmMaintenanceService;

    @RequirePermission(PermissionConstant.ADMIN_MAINTENANCE_VIEW)
    @GetMapping("/list")
    public Result<PageResult<EqmMaintenanceVO>> list(@RequestParam(defaultValue = "1") Long page,
                                                      @RequestParam(defaultValue = "15") Long size) {
        PageResult<EqmMaintenanceVO> pageResult = eqmMaintenanceService.listEqmMaintenances(page, size);
        return Result.success(pageResult);
    }

    @RequirePermission(PermissionConstant.ADMIN_MAINTENANCE_VIEW)
    @GetMapping("/{id}")
    public Result<EqmMaintenanceVO> getById(@PathVariable Long id) {
        EqmMaintenanceVO vo = eqmMaintenanceService.getEqmMaintenanceById(id);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_MAINTENANCE_MANAGE)
    @PostMapping
    public Result<EqmMaintenanceVO> save(@Validated({Default.class, OnCreate.class}) @RequestBody EqmMaintenanceDTO dto) {
        EqmMaintenanceVO vo = eqmMaintenanceService.saveEqmMaintenance(dto);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_MAINTENANCE_MANAGE)
    @PutMapping("/{id}")
    public Result<EqmMaintenanceVO> update(@PathVariable Long id, @Validated @RequestBody EqmMaintenanceDTO dto) {
        EqmMaintenanceVO vo = eqmMaintenanceService.updateEqmMaintenance(id, dto);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_MAINTENANCE_MANAGE)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        eqmMaintenanceService.deleteEqmMaintenance(id);
        return Result.success();
    }
}
