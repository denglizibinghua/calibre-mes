package com.bikeassembly.controller.admin;

import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.dto.SysMenuDTO;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.SysMenuService;
import com.bikeassembly.validation.OnCreate;
import com.bikeassembly.vo.SysMenuVO;
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
@RequestMapping("/admin/sys-menu")
@Slf4j
public class SysMenuController {

    @Autowired
    private SysMenuService sysMenuService;

    @GetMapping("/list")
    @RequirePermission("admin:permission:manage")
    public Result<PageResult<SysMenuVO>> list(@RequestParam(defaultValue = "1") Long page,
                                                @RequestParam(defaultValue = "15") Long size) {
        PageResult<SysMenuVO> pageResult = sysMenuService.listMenus(page, size);
        return Result.success(pageResult);
    }

    @GetMapping("/perms")
    @RequirePermission("admin:permission:manage")
    public Result<List<SysMenuVO>> listAllPerms() {
        List<SysMenuVO> perms = sysMenuService.listAllPerms();
        return Result.success(perms);
    }

    @GetMapping("/tree")
    @RequirePermission("admin:permission:manage")
    public Result<List<SysMenuVO>> listMenuTree() {
        List<SysMenuVO> tree = sysMenuService.listMenuTree();
        return Result.success(tree);
    }

    @GetMapping("/{id}")
    @RequirePermission("admin:permission:manage")
    public Result<SysMenuVO> getById(@PathVariable Long id) {
        SysMenuVO vo = sysMenuService.getMenuById(id);
        return Result.success(vo);
    }

    @PostMapping
    @RequirePermission("admin:permission:manage")
    public Result<SysMenuVO> save(@Validated({Default.class, OnCreate.class}) @RequestBody SysMenuDTO dto) {
        SysMenuVO vo = sysMenuService.saveMenu(dto);
        return Result.success(vo);
    }

    @PutMapping("/{id}")
    @RequirePermission("admin:permission:manage")
    public Result<SysMenuVO> update(@PathVariable Long id, @Validated @RequestBody SysMenuDTO dto) {
        SysMenuVO vo = sysMenuService.updateMenu(id, dto);
        return Result.success(vo);
    }

    @DeleteMapping("/{id}")
    @RequirePermission("admin:permission:manage")
    public Result<Void> delete(@PathVariable Long id) {
        sysMenuService.deleteMenu(id);
        return Result.success();
    }
}
