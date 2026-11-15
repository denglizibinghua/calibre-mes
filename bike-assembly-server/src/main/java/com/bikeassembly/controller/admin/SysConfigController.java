package com.bikeassembly.controller.admin;

import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.PermissionConstant;

import com.bikeassembly.dto.SysConfigDTO;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.SysConfigService;
import com.bikeassembly.vo.SysConfigVO;

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
@RequestMapping("/admin/sys-config")
@Slf4j
public class SysConfigController {

    @Autowired
    private SysConfigService sysConfigService;

    @RequirePermission(PermissionConstant.ADMIN_CONFIG_VIEW)
    @GetMapping("/list")
    public Result<PageResult<SysConfigVO>> list(@RequestParam(defaultValue = "1") Long page,
                                                  @RequestParam(defaultValue = "15") Long size,
                                                  @RequestParam(required = false) String configKey,
                                                  @RequestParam(required = false) String configName) {
        PageResult<SysConfigVO> pageResult = sysConfigService.listConfigs(page, size, configKey, configName);
        return Result.success(pageResult);
    }

    @RequirePermission(PermissionConstant.ADMIN_CONFIG_VIEW)
    @GetMapping("/{id}")
    public Result<SysConfigVO> getById(@PathVariable Long id) {
        SysConfigVO vo = sysConfigService.getConfigById(id);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_CONFIG_MANAGE)
    @PostMapping
    public Result<SysConfigVO> save(@Validated({Default.class, OnCreate.class}) @RequestBody SysConfigDTO dto) {
        SysConfigVO vo = sysConfigService.saveConfig(dto);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_CONFIG_MANAGE)
    @PutMapping("/{id}")
    public Result<SysConfigVO> update(@PathVariable Long id, @Validated @RequestBody SysConfigDTO dto) {
        SysConfigVO vo = sysConfigService.updateConfig(id, dto);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_CONFIG_MANAGE)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysConfigService.deleteConfig(id);
        return Result.success();
    }
}
