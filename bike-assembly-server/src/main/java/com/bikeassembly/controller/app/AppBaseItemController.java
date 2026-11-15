package com.bikeassembly.controller.app;

import com.bikeassembly.result.PageResult;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.BaseItemService;
import com.bikeassembly.vo.BaseItemVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小程序端物料（产品）接口 — 只读查询。
 */
@RestController
@RequestMapping("/app/base-item")
@Slf4j
public class AppBaseItemController {

    @Autowired
    private BaseItemService baseItemService;

    /**
     * 获取全部物料列表（非分页，返回纯数组供小程序端构建名称映射）。
     * GET /app/base-item/list
     */
    @GetMapping("/list")
    public Result<List<BaseItemVO>> list() {
        PageResult<BaseItemVO> pageResult = baseItemService.listItems(1L, 9999L, null, null);
        return Result.success(pageResult.getRecords());
    }

    /**
     * 获取单个物料详情。
     * GET /app/base-item/{id}
     */
    @GetMapping("/{id}")
    public Result<BaseItemVO> getById(@PathVariable Long id) {
        BaseItemVO vo = baseItemService.getItemById(id);
        return Result.success(vo);
    }
}
