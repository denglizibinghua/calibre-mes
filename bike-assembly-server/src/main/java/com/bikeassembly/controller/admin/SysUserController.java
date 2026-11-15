package com.bikeassembly.controller.admin;

import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.PermissionConstant;
import com.bikeassembly.context.BaseContext;
import com.bikeassembly.dto.SysUserDTO;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.SysUserService;
import com.bikeassembly.validation.OnCreate;
import com.bikeassembly.vo.SysUserVO;
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

import java.util.Map;

@RestController
@RequestMapping("/admin/sys-user")
@Slf4j
public class SysUserController {

    @Autowired
    private SysUserService sysUserService;

    @GetMapping("/list")
    @RequirePermission("admin:user:view")
    public Result<PageResult<SysUserVO>> list(@RequestParam(defaultValue = "1") Long page,
                                                @RequestParam(defaultValue = "15") Long size,
                                                @RequestParam(required = false) String username,
                                                @RequestParam(required = false) String nickname) {
        PageResult<SysUserVO> pageResult = sysUserService.listUsers(page, size, username, nickname);
        return Result.success(pageResult);
    }

    @GetMapping("/{id}")
    @RequirePermission("admin:user:view")
    public Result<SysUserVO> getById(@PathVariable Long id) {
        SysUserVO vo = sysUserService.getUserById(id);
        return Result.success(vo);
    }

    @PostMapping
    @RequirePermission("admin:user:manage")
    public Result<SysUserVO> save(@Validated({Default.class, OnCreate.class}) @RequestBody SysUserDTO dto) {
        SysUserVO vo = sysUserService.saveUser(dto);
        return Result.success(vo);
    }

    @PutMapping("/{id}")
    @RequirePermission("admin:user:manage")
    public Result<SysUserVO> update(@PathVariable Long id, @Validated @RequestBody SysUserDTO dto) {
        SysUserVO vo = sysUserService.updateUser(id, dto);
        return Result.success(vo);
    }

    @DeleteMapping("/{id}")
    @RequirePermission("admin:user:manage")
    public Result<Void> delete(@PathVariable Long id) {
        sysUserService.deleteUser(id);
        return Result.success();
    }

    /**
     * PUT /admin/sys-user/change-password
     * 当前用户修改自己的密码。
     */
    @PutMapping("/change-password")
    public Result<Void> changePassword(@RequestBody Map<String, String> body) {
        Long userId = BaseContext.getCurrentId();
        sysUserService.changePassword(userId, body.get("oldPassword"), body.get("newPassword"));
        log.info("用户修改密码：userId={}", userId);
        return Result.success();
    }

    /**
     * PUT /admin/sys-user/{id}/reset-password
     * 管理员重置用户密码。
     */
    @PutMapping("/{id}/reset-password")
    @RequirePermission("admin:user:manage")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        sysUserService.resetPassword(id, body.get("newPassword"));
        log.info("管理员重置密码：targetUserId={}", id);
        return Result.success();
    }

    /**
     * PUT /admin/sys-user/{id}/unlock
     * 管理员解锁账号。
     */
    @PutMapping("/{id}/unlock")
    @RequirePermission(PermissionConstant.ADMIN_USER_UNLOCK)
    public Result<Void> unlock(@PathVariable Long id) {
        sysUserService.unlockAccount(id);
        log.info("管理员解锁账号：userId={}", id);
        return Result.success();
    }
}
