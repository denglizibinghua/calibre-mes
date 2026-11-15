package com.bikeassembly.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bikeassembly.dto.SysOperLogDTO;
import com.bikeassembly.entity.SysOperLog;
import com.bikeassembly.vo.SysOperLogVO;

import com.bikeassembly.result.PageResult;

import java.util.List;

public interface SysOperLogService extends IService<SysOperLog> {

    SysOperLogVO saveOperLog(SysOperLogDTO dto);

    SysOperLogVO updateOperLog(Long id, SysOperLogDTO dto);

    SysOperLogVO getOperLogById(Long id);

    /**
     * 分页查询操作日志。
     *
     * @param page 当前页码（从 1 开始）
     * @param size 每页条数
     */
    PageResult<SysOperLogVO> listOperLogs(Long page, Long size);

    void deleteOperLog(Long id);
}
