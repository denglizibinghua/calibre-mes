package com.bikeassembly.controller.admin;

import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.PermissionConstant;

import com.bikeassembly.dto.SafSafetyEventDTO;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.SafSafetyEventService;
import com.bikeassembly.validation.OnCreate;
import com.bikeassembly.vo.SafSafetyEventVO;
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
@RequestMapping("/admin/saf-safety-event")
@Slf4j
public class SafSafetyEventController {

    @Autowired
    private SafSafetyEventService safSafetyEventService;

    @RequirePermission(PermissionConstant.ADMIN_SAFETY_EVENT_VIEW)
    @GetMapping("/list")
    public Result<PageResult<SafSafetyEventVO>> list(@RequestParam(defaultValue = "1") Long page,
                                                        @RequestParam(defaultValue = "15") Long size) {
        PageResult<SafSafetyEventVO> pageResult = safSafetyEventService.listSafSafetyEvents(page, size);
        return Result.success(pageResult);
    }

    @RequirePermission(PermissionConstant.ADMIN_SAFETY_EVENT_VIEW)
    @GetMapping("/{id}")
    public Result<SafSafetyEventVO> getById(@PathVariable Long id) {
        SafSafetyEventVO vo = safSafetyEventService.getSafSafetyEventById(id);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_SAFETY_EVENT_MANAGE)
    @PostMapping
    public Result<SafSafetyEventVO> save(@Validated({Default.class, OnCreate.class}) @RequestBody SafSafetyEventDTO dto) {
        SafSafetyEventVO vo = safSafetyEventService.saveSafSafetyEvent(dto);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_SAFETY_EVENT_MANAGE)
    @PutMapping("/{id}")
    public Result<SafSafetyEventVO> update(@PathVariable Long id, @Validated @RequestBody SafSafetyEventDTO dto) {
        SafSafetyEventVO vo = safSafetyEventService.updateSafSafetyEvent(id, dto);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_SAFETY_EVENT_MANAGE)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        safSafetyEventService.deleteSafSafetyEvent(id);
        return Result.success();
    }
}
