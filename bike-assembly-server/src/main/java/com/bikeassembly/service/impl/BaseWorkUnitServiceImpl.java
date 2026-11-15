package com.bikeassembly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.dto.BaseWorkUnitDTO;
import com.bikeassembly.entity.BaseWorkUnit;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.mapper.BaseWorkUnitMapper;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.service.BaseWorkUnitService;
import com.bikeassembly.vo.BaseWorkUnitVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import java.util.stream.Collectors;

@Service
@Slf4j
public class BaseWorkUnitServiceImpl extends ServiceImpl<BaseWorkUnitMapper, BaseWorkUnit> implements BaseWorkUnitService {

    @Autowired
    private BaseWorkUnitMapper baseWorkUnitMapper;

    @Override
    public BaseWorkUnitVO saveWorkUnit(BaseWorkUnitDTO dto) {
        checkUnitCodeUnique(dto.getUnitCode(), null);

        BaseWorkUnit entity = toEntity(dto);
        baseWorkUnitMapper.insert(entity);
        log.info("新增生产单元成功：unitId={}, unitCode={}", entity.getUnitId(), entity.getUnitCode());

        return toVO(entity);
    }

    @Override
    public BaseWorkUnitVO updateWorkUnit(Long id, BaseWorkUnitDTO dto) {
        BaseWorkUnit existing = baseWorkUnitMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }

        checkUnitCodeUnique(dto.getUnitCode(), id);

        BaseWorkUnit entity = toEntity(dto);
        entity.setUnitId(id);
        baseWorkUnitMapper.updateById(entity);
        log.info("修改生产单元成功：unitId={}", id);

        BaseWorkUnit updated = baseWorkUnitMapper.selectById(id);
        return toVO(updated);
    }

    @Override
    public BaseWorkUnitVO getWorkUnitById(Long id) {
        BaseWorkUnit entity = baseWorkUnitMapper.selectById(id);
        if (entity == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        return toVO(entity);
    }

    @Override
    public PageResult<BaseWorkUnitVO> listWorkUnits(Long page, Long size) {
        Page<BaseWorkUnit> pageParam = new Page<>(page, size);
        IPage<BaseWorkUnit> pageResult = baseWorkUnitMapper.selectPage(pageParam, null);
        List<BaseWorkUnitVO> records = pageResult.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResult.of(records, pageResult.getTotal(), pageResult.getCurrent(), pageResult.getSize());
    }

    @Override
    public void deleteWorkUnit(Long id) {
        BaseWorkUnit existing = baseWorkUnitMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }

        int refCount = baseWorkUnitMapper.countReferences(id);
        if (refCount > 0) {
            throw new BaseException("该生产单元被 " + refCount + " 条业务数据引用，无法删除");
        }

        baseWorkUnitMapper.deleteById(id);
        log.info("删除生产单元成功：unitId={}", id);
    }

    private void checkUnitCodeUnique(String unitCode, Long excludeId) {
        LambdaQueryWrapper<BaseWorkUnit> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BaseWorkUnit::getUnitCode, unitCode);
        if (excludeId != null) {
            wrapper.ne(BaseWorkUnit::getUnitId, excludeId);
        }
        if (baseWorkUnitMapper.selectCount(wrapper) > 0) {
            throw new BaseException("单元编码已存在：" + unitCode);
        }
    }

    private BaseWorkUnit toEntity(BaseWorkUnitDTO dto) {
        BaseWorkUnit entity = new BaseWorkUnit();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }

    private BaseWorkUnitVO toVO(BaseWorkUnit entity) {
        BaseWorkUnitVO vo = new BaseWorkUnitVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
