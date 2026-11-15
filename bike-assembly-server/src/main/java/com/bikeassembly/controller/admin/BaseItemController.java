package com.bikeassembly.controller.admin;

import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.PermissionConstant;

import com.bikeassembly.dto.BaseItemDTO;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.BaseItemService;
import com.bikeassembly.validation.OnCreate;
import com.bikeassembly.vo.BaseItemVO;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 物料管理 Controller。
 * 只负责接收参数、调用 Service、返回 Result。
 */
@RestController
@RequestMapping("/admin/base-item")
@Slf4j
@RequiredArgsConstructor
public class BaseItemController {

    @Autowired
    private BaseItemService baseItemService;

    @RequirePermission(PermissionConstant.ADMIN_ITEM_VIEW)
    @GetMapping("/list")
    public Result<PageResult<BaseItemVO>> list(@RequestParam(defaultValue = "1") Long page,
                                                @RequestParam(defaultValue = "15") Long size,
                                                @RequestParam(required = false) String itemCode,
                                                @RequestParam(required = false) String itemName) {
        PageResult<BaseItemVO> pageResult = baseItemService.listItems(page, size, itemCode, itemName);
        return Result.success(pageResult);
    }

    @RequirePermission(PermissionConstant.ADMIN_ITEM_VIEW)
    @GetMapping("/{id}")
    public Result<BaseItemVO> getById(@PathVariable Long id) {
        BaseItemVO vo = baseItemService.getItemById(id);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_ITEM_MANAGE)
    @PostMapping
    public Result<BaseItemVO> save(@Validated({Default.class, OnCreate.class}) @RequestBody BaseItemDTO dto) {
        BaseItemVO vo = baseItemService.saveItem(dto);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_ITEM_MANAGE)
    @PutMapping("/{id}")
    public Result<BaseItemVO> update(@PathVariable Long id, @Validated @RequestBody BaseItemDTO dto) {
        BaseItemVO vo = baseItemService.updateItem(id, dto);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_ITEM_MANAGE)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        baseItemService.deleteItem(id);
        return Result.success();
    }
}
