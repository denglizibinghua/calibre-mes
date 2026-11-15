package com.bikeassembly.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.dto.SafSafetyEventDTO;
import com.bikeassembly.entity.SafSafetyEvent;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.mapper.SafSafetyEventMapper;
import com.bikeassembly.service.SafSafetyEventService;
import com.bikeassembly.vo.SafSafetyEventVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;


@Service
@Slf4j
public class SafSafetyEventServiceImpl extends ServiceImpl<SafSafetyEventMapper, SafSafetyEvent> implements SafSafetyEventService {

    @Autowired
    private SafSafetyEventMapper safSafetyEventMapper;

    @Override
    public SafSafetyEventVO saveSafSafetyEvent(SafSafetyEventDTO dto) {
        SafSafetyEvent entity = toEntity(dto);
        safSafetyEventMapper.insert(entity);
        log.info("新增安全事件成功：eventId={}, title={}", entity.getEventId(), entity.getTitle());

        return toVO(entity);
    }

    @Override
    public SafSafetyEventVO updateSafSafetyEvent(Long id, SafSafetyEventDTO dto) {
        SafSafetyEvent existing = safSafetyEventMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }

        SafSafetyEvent entity = toEntity(dto);
        entity.setEventId(id);
        safSafetyEventMapper.updateById(entity);
        log.info("修改安全事件成功：eventId={}", id);

        SafSafetyEvent updated = safSafetyEventMapper.selectById(id);
        return toVO(updated);
    }

    @Override
    public SafSafetyEventVO getSafSafetyEventById(Long id) {
        SafSafetyEvent entity = safSafetyEventMapper.selectById(id);
        if (entity == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        return toVO(entity);
    }

    @Override
    public PageResult<SafSafetyEventVO> listSafSafetyEvents(Long page, Long size) {
        Page<SafSafetyEvent> pageParam = new Page<>(page, size);
        IPage<SafSafetyEvent> pageResult = safSafetyEventMapper.selectPage(pageParam, null);
        List<SafSafetyEventVO> records = pageResult.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResult.of(records, pageResult.getTotal(), pageResult.getCurrent(), pageResult.getSize());
    }

    @Override
    public void deleteSafSafetyEvent(Long id) {
        SafSafetyEvent existing = safSafetyEventMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        safSafetyEventMapper.deleteById(id);
        log.info("删除安全事件成功：eventId={}", id);
    }

    private SafSafetyEvent toEntity(SafSafetyEventDTO dto) {
        SafSafetyEvent entity = new SafSafetyEvent();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }

    private SafSafetyEventVO toVO(SafSafetyEvent entity) {
        SafSafetyEventVO vo = new SafSafetyEventVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
