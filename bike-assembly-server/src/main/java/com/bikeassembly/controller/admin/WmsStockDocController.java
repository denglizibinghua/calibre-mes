package com.bikeassembly.controller.admin;

import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.PermissionConstant;

import com.bikeassembly.dto.WmsStockDocDTO;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.WmsStockDocService;
import com.bikeassembly.validation.OnCreate;
import com.bikeassembly.vo.WmsStockDocVO;
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
@RequestMapping("/admin/wms-stock-doc")
@Slf4j
public class WmsStockDocController {

    @Autowired
    private WmsStockDocService wmsStockDocService;

    @RequirePermission(PermissionConstant.ADMIN_STOCK_DOC_VIEW)
    @GetMapping("/list")
    public Result<PageResult<WmsStockDocVO>> list(@RequestParam(defaultValue = "1") Long page,
                                                    @RequestParam(defaultValue = "15") Long size,
                                                    @RequestParam(required = false) String docNo,
                                                    @RequestParam(required = false) String docType,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) String keyword) {
        PageResult<WmsStockDocVO> pageResult = wmsStockDocService.listWmsStockDocs(page, size,
                docNo, docType, status, keyword);
        return Result.success(pageResult);
    }

    @RequirePermission(PermissionConstant.ADMIN_STOCK_DOC_VIEW)
    @GetMapping("/{id}")
    public Result<WmsStockDocVO> getById(@PathVariable Long id) {
        WmsStockDocVO vo = wmsStockDocService.getWmsStockDocById(id);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_STOCK_DOC_MANAGE)
    @PostMapping
    public Result<WmsStockDocVO> save(@Validated({Default.class, OnCreate.class}) @RequestBody WmsStockDocDTO dto) {
        WmsStockDocVO vo = wmsStockDocService.saveWmsStockDoc(dto);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_STOCK_DOC_MANAGE)
    @PutMapping("/{id}")
    public Result<WmsStockDocVO> update(@PathVariable Long id, @Validated @RequestBody WmsStockDocDTO dto) {
        WmsStockDocVO vo = wmsStockDocService.updateWmsStockDoc(id, dto);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_STOCK_DOC_MANAGE)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        wmsStockDocService.deleteWmsStockDoc(id);
        return Result.success();
    }

    /**
     * PUT /admin/wms-stock-doc/{id}/execute
     * 执行库存单据：校验明细 → 库存增减（出库条件更新防负库存）→ 写流水 → 单据置为已执行。
     */
    @RequirePermission(PermissionConstant.ADMIN_STOCK_DOC_MANAGE)
    @PutMapping("/{id}/execute")
    public Result<WmsStockDocVO> execute(@PathVariable Long id) {
        WmsStockDocVO vo = wmsStockDocService.executeStockDoc(id);
        return Result.success(vo);
    }
}
