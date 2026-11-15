package com.bikeassembly.controller.admin;

import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.PermissionConstant;

import com.bikeassembly.dto.WmsLocationDTO;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.WmsLocationService;
import com.bikeassembly.vo.WmsLocationVO;

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
@RequestMapping("/admin/wms-location")
@Slf4j
public class WmsLocationController {

    @Autowired
    private WmsLocationService wmsLocationService;

    @RequirePermission(PermissionConstant.ADMIN_LOCATION_VIEW)
    @GetMapping("/list")
    public Result<PageResult<WmsLocationVO>> list(@RequestParam(defaultValue = "1") Long page,
                                                    @RequestParam(defaultValue = "15") Long size) {
        PageResult<WmsLocationVO> pageResult = wmsLocationService.listWmsLocations(page, size);
        return Result.success(pageResult);
    }

    @RequirePermission(PermissionConstant.ADMIN_LOCATION_VIEW)
    @GetMapping("/{id}")
    public Result<WmsLocationVO> getById(@PathVariable Long id) {
        WmsLocationVO vo = wmsLocationService.getWmsLocationById(id);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_LOCATION_MANAGE)
    @PostMapping
    public Result<WmsLocationVO> save(@Validated({Default.class, OnCreate.class}) @RequestBody WmsLocationDTO dto) {
        WmsLocationVO vo = wmsLocationService.saveWmsLocation(dto);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_LOCATION_MANAGE)
    @PutMapping("/{id}")
    public Result<WmsLocationVO> update(@PathVariable Long id, @Validated @RequestBody WmsLocationDTO dto) {
        WmsLocationVO vo = wmsLocationService.updateWmsLocation(id, dto);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_LOCATION_MANAGE)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        wmsLocationService.deleteWmsLocation(id);
        return Result.success();
    }
}
