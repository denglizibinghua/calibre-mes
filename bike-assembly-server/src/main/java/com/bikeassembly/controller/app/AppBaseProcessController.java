package com.bikeassembly.controller.app;

import com.bikeassembly.result.PageResult;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.BaseProcessService;
import com.bikeassembly.vo.BaseProcessVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小程序端工序接口 — 只读查询。
 */
@RestController
@RequestMapping("/app/base-process")
@Slf4j
public class AppBaseProcessController {

    @Autowired
    private BaseProcessService baseProcessService;

    /**
     * 获取全部工序列表（非分页，返回纯数组供小程序端构建名称映射）。
     * GET /app/base-process/list
     */
    @GetMapping("/list")
    public Result<List<BaseProcessVO>> list() {
        PageResult<BaseProcessVO> pageResult = baseProcessService.listProcesses(1L, 9999L);
        return Result.success(pageResult.getRecords());
    }

    /**
     * 获取单个工序详情。
     * GET /app/base-process/{id}
     */
    @GetMapping("/{id}")
    public Result<BaseProcessVO> getById(@PathVariable Long id) {
        BaseProcessVO vo = baseProcessService.getProcessById(id);
        return Result.success(vo);
    }
}
