package com.bikeassembly.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.dto.SafTrainingDTO;
import com.bikeassembly.entity.SafTraining;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.mapper.SafTrainingMapper;
import com.bikeassembly.service.SafTrainingService;
import com.bikeassembly.vo.SafTrainingVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;


@Service
@Slf4j
public class SafTrainingServiceImpl extends ServiceImpl<SafTrainingMapper, SafTraining> implements SafTrainingService {

    @Autowired
    private SafTrainingMapper safTrainingMapper;

    @Override
    public SafTrainingVO saveSafTraining(SafTrainingDTO dto) {
        SafTraining entity = toEntity(dto);
        safTrainingMapper.insert(entity);
        log.info("新增培训活动成功：trainingId={}, trainingTitle={}", entity.getTrainingId(), entity.getTrainingTitle());

        return toVO(entity);
    }

    @Override
    public SafTrainingVO updateSafTraining(Long id, SafTrainingDTO dto) {
        SafTraining existing = safTrainingMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }

        SafTraining entity = toEntity(dto);
        entity.setTrainingId(id);
        safTrainingMapper.updateById(entity);
        log.info("修改培训活动成功：trainingId={}", id);

        SafTraining updated = safTrainingMapper.selectById(id);
        return toVO(updated);
    }

    @Override
    public SafTrainingVO getSafTrainingById(Long id) {
        SafTraining entity = safTrainingMapper.selectById(id);
        if (entity == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        return toVO(entity);
    }

    @Override
    public PageResult<SafTrainingVO> listSafTrainings(Long page, Long size) {
        Page<SafTraining> pageParam = new Page<>(page, size);
        IPage<SafTraining> pageResult = safTrainingMapper.selectPage(pageParam, null);
        List<SafTrainingVO> records = pageResult.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResult.of(records, pageResult.getTotal(), pageResult.getCurrent(), pageResult.getSize());
    }

    @Override
    public void deleteSafTraining(Long id) {
        SafTraining existing = safTrainingMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        safTrainingMapper.deleteById(id);
        log.info("删除培训活动成功：trainingId={}", id);
    }

    private SafTraining toEntity(SafTrainingDTO dto) {
        SafTraining entity = new SafTraining();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }

    private SafTrainingVO toVO(SafTraining entity) {
        SafTrainingVO vo = new SafTrainingVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
