package com.bikeassembly.controller.admin;

import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.PermissionConstant;

import com.bikeassembly.dto.WmsInventoryDTO;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.WmsInventoryService;
import com.bikeassembly.validation.OnCreate;
import com.bikeassembly.vo.WmsInventoryVO;
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
@RequestMapping("/admin/wms-inventory")
@Slf4j
public class WmsInventoryController {

    @Autowired
    private WmsInventoryService wmsInventoryService;

    @RequirePermission(PermissionConstant.ADMIN_INVENTORY_VIEW)
    @GetMapping("/list")
    public Result<PageResult<WmsInventoryVO>> list(@RequestParam(required = false) String itemCode,
                                                    @RequestParam(required = false) String itemName,
                                                    @RequestParam(required = false) String warehouseName,
                                                    @RequestParam(defaultValue = "1") Long page,
                                                    @RequestParam(defaultValue = "15") Long size) {
        PageResult<WmsInventoryVO> result = wmsInventoryService.listWmsInventories(itemCode, itemName, warehouseName, page, size);
        return Result.success(result);
    }

    @RequirePermission(PermissionConstant.ADMIN_INVENTORY_VIEW)
    @GetMapping("/{id}")
    public Result<WmsInventoryVO> getById(@PathVariable Long id) {
        WmsInventoryVO vo = wmsInventoryService.getWmsInventoryById(id);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_INVENTORY_MANAGE)
    @PostMapping
    public Result<WmsInventoryVO> save(@Validated({Default.class, OnCreate.class}) @RequestBody WmsInventoryDTO dto) {
        WmsInventoryVO vo = wmsInventoryService.saveWmsInventory(dto);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_INVENTORY_MANAGE)
    @PutMapping("/{id}")
    public Result<WmsInventoryVO> update(@PathVariable Long id, @Validated @RequestBody WmsInventoryDTO dto) {
        WmsInventoryVO vo = wmsInventoryService.updateWmsInventory(id, dto);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_INVENTORY_MANAGE)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        wmsInventoryService.deleteWmsInventory(id);
        return Result.success();
    }
}
