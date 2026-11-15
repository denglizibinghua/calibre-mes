package com.bikeassembly.controller.admin;

import com.bikeassembly.dto.AndonAlertDTO;
import com.bikeassembly.dto.AndonAlertEscalateDTO;
import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.PermissionConstant;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.AndonAlertService;
import com.bikeassembly.validation.OnCreate;
import com.bikeassembly.vo.AndonAlertVO;
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
@RequestMapping("/admin/andon-alert")
@Slf4j
public class AndonAlertController {

    @Autowired
    private AndonAlertService andonAlertService;

    @GetMapping("/list")
    @RequirePermission(PermissionConstant.ADMIN_ANDON_VIEW)
    public Result<PageResult<AndonAlertVO>> list(@RequestParam(defaultValue = "1") Long page,
                                                    @RequestParam(defaultValue = "15") Long size) {
        PageResult<AndonAlertVO> pageResult = andonAlertService.listAndonAlerts(page, size, "all", null);
        return Result.success(pageResult);
    }

    @GetMapping("/{id}")
    @RequirePermission(PermissionConstant.ADMIN_ANDON_VIEW)
    public Result<AndonAlertVO> getById(@PathVariable Long id) {
        AndonAlertVO vo = andonAlertService.getAndonAlertById(id);
        return Result.success(vo);
    }

    @PostMapping
    @RequirePermission(PermissionConstant.ADMIN_ANDON_MANAGE)
    public Result<AndonAlertVO> save(@Validated({Default.class, OnCreate.class}) @RequestBody AndonAlertDTO dto) {
        AndonAlertVO vo = andonAlertService.saveAndonAlert(dto);
        return Result.success(vo);
    }

    @PutMapping("/{id}")
    @RequirePermission(PermissionConstant.ADMIN_ANDON_MANAGE)
    public Result<AndonAlertVO> update(@PathVariable Long id, @Validated @RequestBody AndonAlertDTO dto) {
        AndonAlertVO vo = andonAlertService.updateAndonAlert(id, dto);
        return Result.success(vo);
    }

    /**
     * 受理告警：待处理(0) → 处理中(1)，处理人取当前登录用户。
     */
    @PutMapping("/{id}/accept")
    @RequirePermission(PermissionConstant.ADMIN_ANDON_MANAGE)
    public Result<AndonAlertVO> accept(@PathVariable Long id) {
        AndonAlertVO vo = andonAlertService.acceptAndonAlert(id);
        return Result.success(vo);
    }

    /**
     * 关闭告警：处理中(1) → 已关闭(2)，仅限当前处理人关闭自己受理的告警。
     */
    @PutMapping("/{id}/close")
    @RequirePermission(PermissionConstant.ADMIN_ANDON_MANAGE)
    public Result<AndonAlertVO> close(@PathVariable Long id) {
        AndonAlertVO vo = andonAlertService.closeAndonAlert(id);
        return Result.success(vo);
    }

    /**
     * 升级告警：待处理(0)/处理中(1) 可升级，记录升级人/时间/备注。
     */
    @PutMapping("/{id}/escalate")
    @RequirePermission(PermissionConstant.ADMIN_ANDON_MANAGE)
    public Result<AndonAlertVO> escalate(@PathVariable Long id,
                                         @RequestBody(required = false) AndonAlertEscalateDTO dto) {
        AndonAlertVO vo = andonAlertService.escalateAndonAlert(id, dto);
        return Result.success(vo);
    }

    @DeleteMapping("/{id}")
    @RequirePermission(PermissionConstant.ADMIN_ANDON_MANAGE)
    public Result<Void> delete(@PathVariable Long id) {
        andonAlertService.deleteAndonAlert(id);
        return Result.success();
    }
}
