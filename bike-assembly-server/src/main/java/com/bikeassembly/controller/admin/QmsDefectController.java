package com.bikeassembly.controller.admin;

import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.PermissionConstant;

import com.bikeassembly.dto.QmsDefectDTO;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.QmsDefectService;
import com.bikeassembly.vo.QmsDefectVO;
import lombok.extern.slf4j.Slf4j;
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
@RequestMapping("/admin/qms-defect")
@Slf4j
public class QmsDefectController {

    @Autowired
    private QmsDefectService qmsDefectService;

    @RequirePermission(PermissionConstant.ADMIN_DEFECT_VIEW)
    @GetMapping("/list")
    public Result<PageResult<QmsDefectVO>> list(@RequestParam(defaultValue = "1") Long page,
                                                  @RequestParam(defaultValue = "15") Long size) {
        PageResult<QmsDefectVO> pageResult = qmsDefectService.listQmsDefects(page, size);
        return Result.success(pageResult);
    }

    @RequirePermission(PermissionConstant.ADMIN_DEFECT_VIEW)
    @GetMapping("/{id}")
    public Result<QmsDefectVO> getById(@PathVariable Long id) {
        QmsDefectVO vo = qmsDefectService.getQmsDefectById(id);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_DEFECT_MANAGE)
    @PostMapping
    public Result<QmsDefectVO> save(@RequestBody QmsDefectDTO dto) {
        QmsDefectVO vo = qmsDefectService.saveQmsDefect(dto);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_DEFECT_MANAGE)
    @PutMapping("/{id}")
    public Result<QmsDefectVO> update(@PathVariable Long id, @RequestBody QmsDefectDTO dto) {
        QmsDefectVO vo = qmsDefectService.updateQmsDefect(id, dto);
        return Result.success(vo);
    }

    @RequirePermission(PermissionConstant.ADMIN_DEFECT_MANAGE)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        qmsDefectService.deleteQmsDefect(id);
        return Result.success();
    }
}
