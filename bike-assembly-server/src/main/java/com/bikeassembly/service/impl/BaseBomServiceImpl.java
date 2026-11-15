package com.bikeassembly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.dto.BaseBomDTO;
import com.bikeassembly.entity.BaseBom;
import com.bikeassembly.entity.BaseItem;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.mapper.BaseBomMapper;
import com.bikeassembly.mapper.BaseItemMapper;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.service.BaseBomService;
import com.bikeassembly.vo.BaseBomVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import java.util.stream.Collectors;

@Service
@Slf4j
public class BaseBomServiceImpl extends ServiceImpl<BaseBomMapper, BaseBom> implements BaseBomService {

    @Autowired
    private BaseBomMapper baseBomMapper;

    @Autowired
    private BaseItemMapper baseItemMapper;

    @Override
    public BaseBomVO saveBom(BaseBomDTO dto) {
        checkItemExists(dto.getItemId());
        checkBomCodeUnique(dto.getBomCode(), null);

        BaseBom entity = toEntity(dto);
        baseBomMapper.insert(entity);
        log.info("新增BOM成功：bomId={}, bomCode={}", entity.getBomId(), entity.getBomCode());

        return toVO(entity);
    }

    @Override
    public BaseBomVO updateBom(Long id, BaseBomDTO dto) {
        BaseBom existing = baseBomMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }

        if (dto.getItemId() != null) {
            checkItemExists(dto.getItemId());
        }
        if (dto.getBomCode() != null) {
            checkBomCodeUnique(dto.getBomCode(), id);
        }

        BaseBom entity = toEntity(dto);
        entity.setBomId(id);
        baseBomMapper.updateById(entity);
        log.info("修改BOM成功：bomId={}", id);

        BaseBom updated = baseBomMapper.selectById(id);
        return toVO(updated);
    }

    @Override
    public BaseBomVO getBomById(Long id) {
        BaseBom entity = baseBomMapper.selectById(id);
        if (entity == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        return toVO(entity);
    }

    @Override
    public PageResult<BaseBomVO> listBoms(Long page, Long size) {
        Page<BaseBom> pageParam = new Page<>(page, size);
        IPage<BaseBom> pageResult = baseBomMapper.selectPage(pageParam, null);
        List<BaseBomVO> records = pageResult.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResult.of(records, pageResult.getTotal(), pageResult.getCurrent(), pageResult.getSize());
    }

    @Override
    public void deleteBom(Long id) {
        BaseBom existing = baseBomMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }

        baseBomMapper.deleteById(id);
        log.info("删除BOM成功：bomId={}", id);
    }

    private void checkItemExists(Long itemId) {
        if (baseItemMapper.selectById(itemId) == null) {
            throw new BaseException("物料不存在");
        }
    }

    private void checkBomCodeUnique(String bomCode, Long excludeId) {
        LambdaQueryWrapper<BaseBom> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BaseBom::getBomCode, bomCode);
        if (excludeId != null) {
            wrapper.ne(BaseBom::getBomId, excludeId);
        }
        if (baseBomMapper.selectCount(wrapper) > 0) {
            throw new BaseException("BOM编码已存在：" + bomCode);
        }
    }

    private BaseBom toEntity(BaseBomDTO dto) {
        BaseBom entity = new BaseBom();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }

    private BaseBomVO toVO(BaseBom entity) {
        BaseBomVO vo = new BaseBomVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
