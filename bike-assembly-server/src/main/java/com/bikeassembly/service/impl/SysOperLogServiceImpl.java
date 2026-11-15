package com.bikeassembly.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.dto.SysOperLogDTO;
import com.bikeassembly.entity.SysOperLog;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.mapper.SysOperLogMapper;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.service.SysOperLogService;
import com.bikeassembly.vo.SysOperLogVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class SysOperLogServiceImpl extends ServiceImpl<SysOperLogMapper, SysOperLog> implements SysOperLogService {

    @Autowired
    private SysOperLogMapper sysOperLogMapper;

    @Override
    public SysOperLogVO saveOperLog(SysOperLogDTO dto) {
        SysOperLog entity = toEntity(dto);
        sysOperLogMapper.insert(entity);
        log.info("新增操作日志成功：logId={}", entity.getLogId());

        return toVO(entity);
    }

    @Override
    public SysOperLogVO updateOperLog(Long id, SysOperLogDTO dto) {
        SysOperLog existing = sysOperLogMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }

        SysOperLog entity = toEntity(dto);
        entity.setLogId(id);
        sysOperLogMapper.updateById(entity);
        log.info("修改操作日志成功：logId={}", id);

        SysOperLog updated = sysOperLogMapper.selectById(id);
        return toVO(updated);
    }

    @Override
    public SysOperLogVO getOperLogById(Long id) {
        SysOperLog entity = sysOperLogMapper.selectById(id);
        if (entity == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        return toVO(entity);
    }

    @Override
    public PageResult<SysOperLogVO> listOperLogs(Long page, Long size) {
        Page<SysOperLog> mpPage = new Page<>(page, size);
        IPage<SysOperLog> result = sysOperLogMapper.selectPage(mpPage, null);
        List<SysOperLogVO> records = result.getRecords()
                .stream().map(this::toVO).toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    @Override
    public void deleteOperLog(Long id) {
        SysOperLog existing = sysOperLogMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        sysOperLogMapper.deleteById(id);
        log.info("删除操作日志成功：logId={}", id);
    }

    private SysOperLog toEntity(SysOperLogDTO dto) {
        SysOperLog entity = new SysOperLog();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }

    private SysOperLogVO toVO(SysOperLog entity) {
        SysOperLogVO vo = new SysOperLogVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
