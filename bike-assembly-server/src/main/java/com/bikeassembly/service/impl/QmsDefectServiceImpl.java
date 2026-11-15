package com.bikeassembly.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.dto.QmsDefectDTO;
import com.bikeassembly.entity.QmsDefect;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.mapper.QmsDefectMapper;
import com.bikeassembly.service.QmsDefectService;
import com.bikeassembly.vo.QmsDefectVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;


@Service
@Slf4j
public class QmsDefectServiceImpl extends ServiceImpl<QmsDefectMapper, QmsDefect> implements QmsDefectService {

    @Autowired
    private QmsDefectMapper qmsDefectMapper;

    @Override
    public QmsDefectVO saveQmsDefect(QmsDefectDTO dto) {
        QmsDefect entity = toEntity(dto);
        qmsDefectMapper.insert(entity);
        log.info("新增缺陷记录成功：defectId={}", entity.getDefectId());

        return toVO(entity);
    }

    @Override
    public QmsDefectVO updateQmsDefect(Long id, QmsDefectDTO dto) {
        QmsDefect existing = qmsDefectMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }

        QmsDefect entity = toEntity(dto);
        entity.setDefectId(id);
        qmsDefectMapper.updateById(entity);
        log.info("修改缺陷记录成功：defectId={}", id);

        QmsDefect updated = qmsDefectMapper.selectById(id);
        return toVO(updated);
    }

    @Override
    public QmsDefectVO getQmsDefectById(Long id) {
        QmsDefect entity = qmsDefectMapper.selectById(id);
        if (entity == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        return toVO(entity);
    }

    @Override
    public PageResult<QmsDefectVO> listQmsDefects(Long page, Long size) {
        Page<QmsDefect> pageParam = new Page<>(page, size);
        IPage<QmsDefect> pageResult = qmsDefectMapper.selectPage(pageParam, null);
        List<QmsDefectVO> records = pageResult.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResult.of(records, pageResult.getTotal(), pageResult.getCurrent(), pageResult.getSize());
    }

    @Override
    public void deleteQmsDefect(Long id) {
        QmsDefect existing = qmsDefectMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        qmsDefectMapper.deleteById(id);
        log.info("删除缺陷记录成功：defectId={}", id);
    }

    private QmsDefect toEntity(QmsDefectDTO dto) {
        QmsDefect entity = new QmsDefect();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }

    private QmsDefectVO toVO(QmsDefect entity) {
        QmsDefectVO vo = new QmsDefectVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
