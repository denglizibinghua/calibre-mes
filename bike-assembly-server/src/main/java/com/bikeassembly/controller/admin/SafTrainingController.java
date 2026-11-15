package com.bikeassembly.controller.admin;

import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.PermissionConstant;

import com.bikeassembly.dto.SafTrainingDTO;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.SafTrainingService;
import com.bikeassembly.vo.SafTrainingVO;

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
@RequestMapping("/admin/saf-training")
@Slf4j
public class SafTrainingController {

    @Autowired
    private SafTrainingService safTrainingService;

    @RequirePermission(PermissionConstant.ADMIN_SAFETY_TRAINING_VIEW)
    @GetMapping("/list")
    public Result<PageResult<SafTrainingVO>> list(@RequestParam(defaultValue = "1") Long page,
                                                    @RequestParam(defaultValue = "15") Long size) {
        PageResult<SafTrainingVO> pageResult = safTrainingService.listSafTrainings(page, size);
        return Result.success(pageResult);
    }

    @RequirePermission(PermissionConstant.ADMIN_SAFETY_TRAINING_VIEW)
    @GetMapping("/{id}")
    public Result<SafTrainingVO> getById(@PathVariable Long id) {
        SafTrainingVO vo = safTrainingService.getSafTrainingById(id);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_SAFETY_TRAINING_MANAGE)
    @PostMapping
    public Result<SafTrainingVO> save(@Validated({Default.class, OnCreate.class}) @RequestBody SafTrainingDTO dto) {
        SafTrainingVO vo = safTrainingService.saveSafTraining(dto);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_SAFETY_TRAINING_MANAGE)
    @PutMapping("/{id}")
    public Result<SafTrainingVO> update(@PathVariable Long id, @Validated @RequestBody SafTrainingDTO dto) {
        SafTrainingVO vo = safTrainingService.updateSafTraining(id, dto);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_SAFETY_TRAINING_MANAGE)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        safTrainingService.deleteSafTraining(id);
        return Result.success();
    }
}
