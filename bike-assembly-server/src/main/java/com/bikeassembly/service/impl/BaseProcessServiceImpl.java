package com.bikeassembly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.dto.BaseProcessDTO;
import com.bikeassembly.entity.BaseProcess;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.mapper.BaseProcessMapper;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.service.BaseProcessService;
import com.bikeassembly.vo.BaseProcessVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import java.util.stream.Collectors;

@Service
@Slf4j
public class BaseProcessServiceImpl extends ServiceImpl<BaseProcessMapper, BaseProcess> implements BaseProcessService {

    @Autowired
    private BaseProcessMapper baseProcessMapper;

    @Override
    public BaseProcessVO saveProcess(BaseProcessDTO dto) {
        checkProcessCodeUnique(dto.getProcessCode(), null);

        BaseProcess entity = toEntity(dto);
        baseProcessMapper.insert(entity);
        log.info("新增工序成功：processId={}, processCode={}", entity.getProcessId(), entity.getProcessCode());

        return toVO(entity);
    }

    @Override
    public BaseProcessVO updateProcess(Long id, BaseProcessDTO dto) {
        BaseProcess existing = baseProcessMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }

        checkProcessCodeUnique(dto.getProcessCode(), id);

        BaseProcess entity = toEntity(dto);
        entity.setProcessId(id);
        baseProcessMapper.updateById(entity);
        log.info("修改工序成功：processId={}", id);

        BaseProcess updated = baseProcessMapper.selectById(id);
        return toVO(updated);
    }

    @Override
    public BaseProcessVO getProcessById(Long id) {
        BaseProcess entity = baseProcessMapper.selectById(id);
        if (entity == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        return toVO(entity);
    }

    @Override
    public PageResult<BaseProcessVO> listProcesses(Long page, Long size) {
        Page<BaseProcess> pageParam = new Page<>(page, size);
        IPage<BaseProcess> pageResult = baseProcessMapper.selectPage(pageParam, null);
        List<BaseProcessVO> records = pageResult.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResult.of(records, pageResult.getTotal(), pageResult.getCurrent(), pageResult.getSize());
    }

    @Override
    public void deleteProcess(Long id) {
        BaseProcess existing = baseProcessMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }

        int refCount = baseProcessMapper.countReferences(id);
        if (refCount > 0) {
            throw new BaseException("该工序被 " + refCount + " 条业务数据引用，无法删除");
        }

        baseProcessMapper.deleteById(id);
        log.info("删除工序成功：processId={}", id);
    }

    private void checkProcessCodeUnique(String processCode, Long excludeId) {
        LambdaQueryWrapper<BaseProcess> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BaseProcess::getProcessCode, processCode);
        if (excludeId != null) {
            wrapper.ne(BaseProcess::getProcessId, excludeId);
        }
        if (baseProcessMapper.selectCount(wrapper) > 0) {
            throw new BaseException("工序编码已存在：" + processCode);
        }
    }

    private BaseProcess toEntity(BaseProcessDTO dto) {
        BaseProcess entity = new BaseProcess();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }

    private BaseProcessVO toVO(BaseProcess entity) {
        BaseProcessVO vo = new BaseProcessVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
