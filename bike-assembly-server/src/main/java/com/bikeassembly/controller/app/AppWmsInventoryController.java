package com.bikeassembly.controller.app;

import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.PermissionConstant;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.WmsInventoryService;
import com.bikeassembly.vo.WmsInventoryVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小程序端库存查询接口 — 只读，公共数据无需 scope 控制。
 */
@RestController
@RequestMapping("/app/wms-inventory")
@Slf4j
public class AppWmsInventoryController {

    @Autowired
    private WmsInventoryService wmsInventoryService;

    /**
     * 获取库存列表。
     */
    @GetMapping("/list")
    @RequirePermission(PermissionConstant.APP_INVENTORY_VIEW)
    public Result<List<WmsInventoryVO>> list(@RequestParam(required = false) String itemName,
                                              @RequestParam(required = false) String itemCode) {
        PageResult<WmsInventoryVO> pageResult = wmsInventoryService.listWmsInventories(
                itemCode, itemName, null, 1L, 9999L);
        return Result.success(pageResult.getRecords());
    }

    /**
     * 获取单条库存详情。
     */
    @GetMapping("/{id}")
    @RequirePermission(PermissionConstant.APP_INVENTORY_VIEW)
    public Result<WmsInventoryVO> getById(@PathVariable Long id) {
        WmsInventoryVO vo = wmsInventoryService.getWmsInventoryById(id);
        return Result.success(vo);
    }
}
