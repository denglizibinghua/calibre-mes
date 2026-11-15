package com.bikeassembly.controller.admin;

import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.PermissionConstant;

import com.bikeassembly.dto.BaseBomDTO;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.BaseBomService;
import com.bikeassembly.validation.OnCreate;
import com.bikeassembly.vo.BaseBomVO;
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
@RequestMapping("/admin/base-bom")
@Slf4j
public class BaseBomController {

    @Autowired
    private BaseBomService baseBomService;

    @RequirePermission(PermissionConstant.ADMIN_BOM_VIEW)
    @GetMapping("/list")
    public Result<PageResult<BaseBomVO>> list(
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "15") Long size) {
        PageResult<BaseBomVO> pageResult = baseBomService.listBoms(page, size);
        return Result.success(pageResult);
    }

    @RequirePermission(PermissionConstant.ADMIN_BOM_VIEW)
    @GetMapping("/{id}")
    public Result<BaseBomVO> getById(@PathVariable Long id) {
        BaseBomVO vo = baseBomService.getBomById(id);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_BOM_MANAGE)
    @PostMapping
    public Result<BaseBomVO> save(@Validated({Default.class, OnCreate.class}) @RequestBody BaseBomDTO dto) {
        BaseBomVO vo = baseBomService.saveBom(dto);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_BOM_MANAGE)
    @PutMapping("/{id}")
    public Result<BaseBomVO> update(@PathVariable Long id, @Validated @RequestBody BaseBomDTO dto) {
        BaseBomVO vo = baseBomService.updateBom(id, dto);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_BOM_MANAGE)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        baseBomService.deleteBom(id);
        return Result.success();
    }
}
