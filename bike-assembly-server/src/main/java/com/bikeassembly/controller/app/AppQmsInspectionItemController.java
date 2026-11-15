package com.bikeassembly.controller.app;

import com.bikeassembly.result.PageResult;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.QmsInspectionItemService;
import com.bikeassembly.vo.QmsInspectionItemVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 小程序端质检项参考数据接口 — 只读查询。
 */
@RestController
@RequestMapping("/app/qms-inspection-item")
@Slf4j
public class AppQmsInspectionItemController {

    @Autowired
    private QmsInspectionItemService qmsInspectionItemService;

    /**
     * 获取质检项列表（非分页，可按工序筛选）。
     * GET /app/qms-inspection-item/list
     */
    @GetMapping("/list")
    public Result<List<QmsInspectionItemVO>> list(@RequestParam(required = false) Long processId) {
        PageResult<QmsInspectionItemVO> pageResult = qmsInspectionItemService.listQmsInspectionItems(1L, 9999L);
        List<QmsInspectionItemVO> list = pageResult.getRecords();
        if (processId != null) {
            list = list.stream().filter(v -> processId.equals(v.getProcessId())).collect(Collectors.toList());
        }
        return Result.success(list);
    }
}
