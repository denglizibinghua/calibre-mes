package com.bikeassembly.controller.admin;

import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.PermissionConstant;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.QmsInspectionService;
import com.bikeassembly.vo.QmsInspectionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端质检单只读接口：质检录入/确认仅在小程序端（/app/**）执行。
 * 管理端只保留列表/详情查看，配合"质检单只读总览"页面使用。
 */
@RestController
@RequestMapping("/admin/qms-inspection")
@Slf4j
public class QmsInspectionController {

    @Autowired
    private QmsInspectionService qmsInspectionService;

    @GetMapping("/list")
    @RequirePermission(PermissionConstant.ADMIN_INSPECTION_VIEW)
    public Result<PageResult<QmsInspectionVO>> list(@RequestParam(defaultValue = "1") Long page,
                                                     @RequestParam(defaultValue = "15") Long size,
                                                     @RequestParam(required = false) String inspectionNo,
                                                     @RequestParam(required = false) String itemName,
                                                     @RequestParam(required = false) String inspector,
                                                     @RequestParam(required = false) Long processRecordId) {
        PageResult<QmsInspectionVO> pageResult =
                qmsInspectionService.listQmsInspections(
                        page, size, inspectionNo, itemName, inspector, "all", processRecordId);
        return Result.success(pageResult);
    }

    @GetMapping("/{id}")
    @RequirePermission(PermissionConstant.ADMIN_INSPECTION_VIEW)
    public Result<QmsInspectionVO> getById(@PathVariable Long id) {
        QmsInspectionVO vo = qmsInspectionService.getQmsInspectionById(id);
        return Result.success(vo);
    }
}
