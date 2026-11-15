package com.bikeassembly.controller.admin;

import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.dto.SysRoleDTO;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.SysRoleMenuService;
import com.bikeassembly.service.SysRoleService;
import com.bikeassembly.validation.OnCreate;
import com.bikeassembly.vo.SysRoleVO;
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

import java.util.List;

@RestController
@RequestMapping("/admin/sys-role")
@Slf4j
public class SysRoleController {

    @Autowired
    private SysRoleService sysRoleService;

    @Autowired
    private SysRoleMenuService sysRoleMenuService;

    @GetMapping("/list")
    @RequirePermission("admin:role:view")
    public Result<PageResult<SysRoleVO>> list(@RequestParam(defaultValue = "1") Long page,
                                                @RequestParam(defaultValue = "15") Long size) {
        PageResult<SysRoleVO> pageResult = sysRoleService.listRoles(page, size);
        return Result.success(pageResult);
    }

    @GetMapping("/{id}")
    @RequirePermission("admin:role:view")
    public Result<SysRoleVO> getById(@PathVariable Long id) {
        SysRoleVO vo = sysRoleService.getRoleById(id);
        return Result.success(vo);
    }

    @PostMapping
    @RequirePermission("admin:role:manage")
    public Result<SysRoleVO> save(@Validated({Default.class, OnCreate.class}) @RequestBody SysRoleDTO dto) {
        SysRoleVO vo = sysRoleService.saveRole(dto);
        return Result.success(vo);
    }

    @PutMapping("/{id}")
    @RequirePermission("admin:role:manage")
    public Result<SysRoleVO> update(@PathVariable Long id, @Validated @RequestBody SysRoleDTO dto) {
        SysRoleVO vo = sysRoleService.updateRole(id, dto);
        return Result.success(vo);
    }

    @DeleteMapping("/{id}")
    @RequirePermission("admin:role:manage")
    public Result<Void> delete(@PathVariable Long id) {
        sysRoleService.deleteRole(id);
        return Result.success();
    }

    @GetMapping("/{id}/menus")
    @RequirePermission("admin:role:view")
    public Result<List<Long>> getMenusByRoleId(@PathVariable Long id) {
        List<Long> menuIds = sysRoleMenuService.getMenuIdsByRoleId(id);
        return Result.success(menuIds);
    }

    @PutMapping("/{id}/menus")
    @RequirePermission("admin:role:manage")
    public Result<Void> assignMenus(@PathVariable Long id, @RequestBody List<Long> menuIds) {
        sysRoleMenuService.assignMenus(id, menuIds);
        return Result.success();
    }
}
