package com.bikeassembly.controller.admin;

import com.bikeassembly.dto.BaseEquipmentDTO;
import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.PermissionConstant;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.BaseEquipmentService;
import com.bikeassembly.validation.OnCreate;
import com.bikeassembly.vo.BaseEquipmentVO;
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
@RequestMapping("/admin/base-equipment")
@Slf4j
public class BaseEquipmentController {

    @Autowired
    private BaseEquipmentService baseEquipmentService;

    @GetMapping("/list")
    @RequirePermission(PermissionConstant.ADMIN_EQUIPMENT_VIEW)
    public Result<PageResult<BaseEquipmentVO>> list(@RequestParam(defaultValue = "1") Long page,
                                                      @RequestParam(defaultValue = "15") Long size,
                                                      @RequestParam(required = false) String equipmentCode,
                                                      @RequestParam(required = false) String equipmentName) {
        PageResult<BaseEquipmentVO> pageResult = baseEquipmentService.listEquipments(page, size, equipmentCode, equipmentName);
        return Result.success(pageResult);
    }

    @GetMapping("/{id}")
    @RequirePermission(PermissionConstant.ADMIN_EQUIPMENT_VIEW)
    public Result<BaseEquipmentVO> getById(@PathVariable Long id) {
        BaseEquipmentVO vo = baseEquipmentService.getEquipmentById(id);
        return Result.success(vo);
    }

    @PostMapping
    @RequirePermission(PermissionConstant.ADMIN_EQUIPMENT_MANAGE)
    public Result<BaseEquipmentVO> save(@Validated({Default.class, OnCreate.class}) @RequestBody BaseEquipmentDTO dto) {
        BaseEquipmentVO vo = baseEquipmentService.saveEquipment(dto);
        return Result.success(vo);
    }

    @PutMapping("/{id}")
    @RequirePermission(PermissionConstant.ADMIN_EQUIPMENT_MANAGE)
    public Result<BaseEquipmentVO> update(@PathVariable Long id, @Validated @RequestBody BaseEquipmentDTO dto) {
        BaseEquipmentVO vo = baseEquipmentService.updateEquipment(id, dto);
        return Result.success(vo);
    }

    @DeleteMapping("/{id}")
    @RequirePermission(PermissionConstant.ADMIN_EQUIPMENT_MANAGE)
    public Result<Void> delete(@PathVariable Long id) {
        baseEquipmentService.deleteEquipment(id);
        return Result.success();
    }
}
