package com.bikeassembly.controller.app;

import com.bikeassembly.result.PageResult;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.BaseEquipmentService;
import com.bikeassembly.vo.BaseEquipmentVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小程序端设备接口 — 只读查询。
 */
@RestController
@RequestMapping("/app/base-equipment")
@Slf4j
public class AppBaseEquipmentController {

    @Autowired
    private BaseEquipmentService baseEquipmentService;

    /**
     * 获取全部设备列表（非分页，返回纯数组供小程序端构建名称映射）。
     * GET /app/base-equipment/list
     */
    @GetMapping("/list")
    public Result<List<BaseEquipmentVO>> list() {
        PageResult<BaseEquipmentVO> pageResult = baseEquipmentService.listEquipments(1L, 9999L, null, null);
        return Result.success(pageResult.getRecords());
    }

    /**
     * 获取单个设备详情。
     * GET /app/base-equipment/{id}
     */
    @GetMapping("/{id}")
    public Result<BaseEquipmentVO> getById(@PathVariable Long id) {
        BaseEquipmentVO vo = baseEquipmentService.getEquipmentById(id);
        return Result.success(vo);
    }
}
