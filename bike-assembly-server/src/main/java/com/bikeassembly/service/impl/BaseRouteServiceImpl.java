package com.bikeassembly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.dto.BaseRouteDTO;
import com.bikeassembly.entity.BaseItem;
import com.bikeassembly.entity.BaseRoute;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.mapper.BaseItemMapper;
import com.bikeassembly.mapper.BaseRouteMapper;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.service.BaseRouteService;
import com.bikeassembly.vo.BaseRouteVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import java.util.stream.Collectors;

@Service
@Slf4j
public class BaseRouteServiceImpl extends ServiceImpl<BaseRouteMapper, BaseRoute> implements BaseRouteService {

    @Autowired
    private BaseRouteMapper baseRouteMapper;

    @Autowired
    private BaseItemMapper baseItemMapper;

    @Override
    public BaseRouteVO saveRoute(BaseRouteDTO dto) {
        checkItemExists(dto.getItemId());
        checkRouteCodeUnique(dto.getRouteCode(), null);

        BaseRoute entity = toEntity(dto);
        baseRouteMapper.insert(entity);
        log.info("新增工艺路线成功：routeId={}, routeCode={}", entity.getRouteId(), entity.getRouteCode());

        return toVO(entity);
    }

    @Override
    public BaseRouteVO updateRoute(Long id, BaseRouteDTO dto) {
        BaseRoute existing = baseRouteMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }

        if (dto.getItemId() != null) {
            checkItemExists(dto.getItemId());
        }
        if (dto.getRouteCode() != null) {
            checkRouteCodeUnique(dto.getRouteCode(), id);
        }

        BaseRoute entity = toEntity(dto);
        entity.setRouteId(id);
        baseRouteMapper.updateById(entity);
        log.info("修改工艺路线成功：routeId={}", id);

        BaseRoute updated = baseRouteMapper.selectById(id);
        return toVO(updated);
    }

    @Override
    public BaseRouteVO getRouteById(Long id) {
        BaseRoute entity = baseRouteMapper.selectById(id);
        if (entity == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        return toVO(entity);
    }

    @Override
    public PageResult<BaseRouteVO> listRoutes(Long page, Long size) {
        Page<BaseRoute> pageParam = new Page<>(page, size);
        IPage<BaseRoute> pageResult = baseRouteMapper.selectPage(pageParam, null);
        List<BaseRouteVO> records = pageResult.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResult.of(records, pageResult.getTotal(), pageResult.getCurrent(), pageResult.getSize());
    }

    @Override
    public void deleteRoute(Long id) {
        BaseRoute existing = baseRouteMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }

        int refCount = baseRouteMapper.countReferences(id);
        if (refCount > 0) {
            throw new BaseException("该工艺路线被 " + refCount + " 条业务数据引用，无法删除");
        }

        baseRouteMapper.deleteById(id);
        log.info("删除工艺路线成功：routeId={}", id);
    }

    private void checkItemExists(Long itemId) {
        if (baseItemMapper.selectById(itemId) == null) {
            throw new BaseException("物料不存在");
        }
    }

    private void checkRouteCodeUnique(String routeCode, Long excludeId) {
        LambdaQueryWrapper<BaseRoute> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BaseRoute::getRouteCode, routeCode);
        if (excludeId != null) {
            wrapper.ne(BaseRoute::getRouteId, excludeId);
        }
        if (baseRouteMapper.selectCount(wrapper) > 0) {
            throw new BaseException("工艺路线编码已存在：" + routeCode);
        }
    }

    private BaseRoute toEntity(BaseRouteDTO dto) {
        BaseRoute entity = new BaseRoute();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }

    private BaseRouteVO toVO(BaseRoute entity) {
        BaseRouteVO vo = new BaseRouteVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
