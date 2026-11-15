package com.bikeassembly.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.dto.QmsInspectionItemDTO;
import com.bikeassembly.entity.QmsInspectionItem;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.mapper.QmsInspectionItemMapper;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.service.QmsInspectionItemService;
import com.bikeassembly.vo.QmsInspectionItemVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import java.util.stream.Collectors;

@Service
@Slf4j
public class QmsInspectionItemServiceImpl extends ServiceImpl<QmsInspectionItemMapper, QmsInspectionItem> implements QmsInspectionItemService {

    @Autowired
    private QmsInspectionItemMapper qmsInspectionItemMapper;

    @Override
    public QmsInspectionItemVO saveQmsInspectionItem(QmsInspectionItemDTO dto) {
        QmsInspectionItem entity = toEntity(dto);
        qmsInspectionItemMapper.insert(entity);
        log.info("新增质检项成功：itemId={}", entity.getInspectionItemId());

        return toVO(entity);
    }

    @Override
    public QmsInspectionItemVO updateQmsInspectionItem(Long id, QmsInspectionItemDTO dto) {
        QmsInspectionItem existing = qmsInspectionItemMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }

        QmsInspectionItem entity = toEntity(dto);
        entity.setInspectionItemId(id);
        qmsInspectionItemMapper.updateById(entity);
        log.info("修改质检项成功：itemId={}", id);

        QmsInspectionItem updated = qmsInspectionItemMapper.selectById(id);
        return toVO(updated);
    }

    @Override
    public QmsInspectionItemVO getQmsInspectionItemById(Long id) {
        QmsInspectionItem entity = qmsInspectionItemMapper.selectById(id);
        if (entity == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        return toVO(entity);
    }

    @Override
    public PageResult<QmsInspectionItemVO> listQmsInspectionItems(Long page, Long size) {
        Page<QmsInspectionItem> pageParam = new Page<>(page, size);
        IPage<QmsInspectionItem> pageResult = qmsInspectionItemMapper.selectPage(pageParam, null);
        List<QmsInspectionItemVO> records = pageResult.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResult.of(records, pageResult.getTotal(), pageResult.getCurrent(), pageResult.getSize());
    }

    @Override
    public void deleteQmsInspectionItem(Long id) {
        QmsInspectionItem existing = qmsInspectionItemMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        qmsInspectionItemMapper.deleteById(id);
        log.info("删除质检项成功：itemId={}", id);
    }

    private QmsInspectionItem toEntity(QmsInspectionItemDTO dto) {
        QmsInspectionItem entity = new QmsInspectionItem();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }

    private QmsInspectionItemVO toVO(QmsInspectionItem entity) {
        QmsInspectionItemVO vo = new QmsInspectionItemVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
