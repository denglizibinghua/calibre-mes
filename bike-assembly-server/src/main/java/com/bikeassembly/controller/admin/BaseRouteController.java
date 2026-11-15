package com.bikeassembly.controller.admin;

import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.PermissionConstant;

import com.bikeassembly.dto.BaseRouteDTO;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.BaseRouteService;
import com.bikeassembly.validation.OnCreate;
import com.bikeassembly.vo.BaseRouteVO;
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
@RequestMapping("/admin/base-route")
@Slf4j
public class BaseRouteController {

    @Autowired
    private BaseRouteService baseRouteService;

    @RequirePermission(PermissionConstant.ADMIN_ROUTE_VIEW)
    @GetMapping("/list")
    public Result<PageResult<BaseRouteVO>> list(
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "15") Long size) {
        PageResult<BaseRouteVO> pageResult = baseRouteService.listRoutes(page, size);
        return Result.success(pageResult);
    }

    @RequirePermission(PermissionConstant.ADMIN_ROUTE_VIEW)
    @GetMapping("/{id}")
    public Result<BaseRouteVO> getById(@PathVariable Long id) {
        BaseRouteVO vo = baseRouteService.getRouteById(id);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_ROUTE_MANAGE)
    @PostMapping
    public Result<BaseRouteVO> save(@Validated({Default.class, OnCreate.class}) @RequestBody BaseRouteDTO dto) {
        BaseRouteVO vo = baseRouteService.saveRoute(dto);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_ROUTE_MANAGE)
    @PutMapping("/{id}")
    public Result<BaseRouteVO> update(@PathVariable Long id, @Validated @RequestBody BaseRouteDTO dto) {
        BaseRouteVO vo = baseRouteService.updateRoute(id, dto);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_ROUTE_MANAGE)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        baseRouteService.deleteRoute(id);
        return Result.success();
    }
}
