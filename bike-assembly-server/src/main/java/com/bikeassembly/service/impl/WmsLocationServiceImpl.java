package com.bikeassembly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.dto.WmsLocationDTO;
import com.bikeassembly.entity.WmsLocation;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.mapper.WmsLocationMapper;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.service.WmsLocationService;
import com.bikeassembly.vo.WmsLocationVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import java.util.stream.Collectors;

@Service
@Slf4j
public class WmsLocationServiceImpl extends ServiceImpl<WmsLocationMapper, WmsLocation> implements WmsLocationService {

    @Autowired
    private WmsLocationMapper wmsLocationMapper;

    @Override
    public WmsLocationVO saveWmsLocation(WmsLocationDTO dto) {
        checkLocationCodeUnique(dto.getLocationCode(), null);

        WmsLocation entity = toEntity(dto);
        wmsLocationMapper.insert(entity);
        log.info("新增仓储位置成功：locationId={}, locationCode={}", entity.getLocationId(), entity.getLocationCode());

        return toVO(entity);
    }

    @Override
    public WmsLocationVO updateWmsLocation(Long id, WmsLocationDTO dto) {
        WmsLocation existing = wmsLocationMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }

        checkLocationCodeUnique(dto.getLocationCode(), id);

        WmsLocation entity = toEntity(dto);
        entity.setLocationId(id);
        wmsLocationMapper.updateById(entity);
        log.info("修改仓储位置成功：locationId={}", id);

        WmsLocation updated = wmsLocationMapper.selectById(id);
        return toVO(updated);
    }

    @Override
    public WmsLocationVO getWmsLocationById(Long id) {
        WmsLocation entity = wmsLocationMapper.selectById(id);
        if (entity == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        return toVO(entity);
    }

    @Override
    public PageResult<WmsLocationVO> listWmsLocations(Long page, Long size) {
        Page<WmsLocation> pageParam = new Page<>(page, size);
        IPage<WmsLocation> pageResult = wmsLocationMapper.selectPage(pageParam, null);
        List<WmsLocationVO> records = pageResult.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResult.of(records, pageResult.getTotal(), pageResult.getCurrent(), pageResult.getSize());
    }

    @Override
    public void deleteWmsLocation(Long id) {
        WmsLocation existing = wmsLocationMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }

        int refCount = wmsLocationMapper.countReferences(id);
        if (refCount > 0) {
            throw new BaseException("该库位被 " + refCount + " 条业务数据引用，无法删除");
        }

        wmsLocationMapper.deleteById(id);
        log.info("删除仓储位置成功：locationId={}", id);
    }

    private void checkLocationCodeUnique(String locationCode, Long excludeId) {
        LambdaQueryWrapper<WmsLocation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WmsLocation::getLocationCode, locationCode);
        if (excludeId != null) {
            wrapper.ne(WmsLocation::getLocationId, excludeId);
        }
        if (wmsLocationMapper.selectCount(wrapper) > 0) {
            throw new BaseException("位置编码已存在：" + locationCode);
        }
    }

    private WmsLocation toEntity(WmsLocationDTO dto) {
        WmsLocation entity = new WmsLocation();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }

    private WmsLocationVO toVO(WmsLocation entity) {
        WmsLocationVO vo = new WmsLocationVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
