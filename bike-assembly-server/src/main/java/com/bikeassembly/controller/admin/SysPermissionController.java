package com.bikeassembly.controller.admin;

import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.PermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/sys-permission")
@Slf4j
public class SysPermissionController {

    @Autowired
    private PermissionService permissionService;

    @DeleteMapping("/cache")
    @RequirePermission("admin:permission:manage")
    public Result<Void> clearCache() {
        permissionService.evictAllCache();
        return Result.success();
    }
}
