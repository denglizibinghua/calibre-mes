package com.bikeassembly.controller.admin;

import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.PermissionConstant;

import com.bikeassembly.dto.QmsInspectionItemDTO;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.QmsInspectionItemService;
import com.bikeassembly.vo.QmsInspectionItemVO;

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
@RequestMapping("/admin/qms-inspection-item")
@Slf4j
public class QmsInspectionItemController {

    @Autowired
    private QmsInspectionItemService qmsInspectionItemService;

    @RequirePermission(PermissionConstant.ADMIN_INSPECTION_ITEM_VIEW)
    @GetMapping("/list")
    public Result<PageResult<QmsInspectionItemVO>> list(@RequestParam(defaultValue = "1") Long page,
                                                          @RequestParam(defaultValue = "15") Long size) {
        PageResult<QmsInspectionItemVO> pageResult = qmsInspectionItemService.listQmsInspectionItems(page, size);
        return Result.success(pageResult);
    }

    @RequirePermission(PermissionConstant.ADMIN_INSPECTION_ITEM_VIEW)
    @GetMapping("/{id}")
    public Result<QmsInspectionItemVO> getById(@PathVariable Long id) {
        QmsInspectionItemVO vo = qmsInspectionItemService.getQmsInspectionItemById(id);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_INSPECTION_ITEM_MANAGE)
    @PostMapping
    public Result<QmsInspectionItemVO> save(@Validated({Default.class, OnCreate.class}) @RequestBody QmsInspectionItemDTO dto) {
        QmsInspectionItemVO vo = qmsInspectionItemService.saveQmsInspectionItem(dto);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_INSPECTION_ITEM_MANAGE)
    @PutMapping("/{id}")
    public Result<QmsInspectionItemVO> update(@PathVariable Long id, @Validated @RequestBody QmsInspectionItemDTO dto) {
        QmsInspectionItemVO vo = qmsInspectionItemService.updateQmsInspectionItem(id, dto);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_INSPECTION_ITEM_MANAGE)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        qmsInspectionItemService.deleteQmsInspectionItem(id);
        return Result.success();
    }
}
