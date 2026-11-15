package com.bikeassembly.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.dto.EqmMaintenanceDTO;
import com.bikeassembly.entity.EqmMaintenance;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.mapper.BaseEquipmentMapper;
import com.bikeassembly.mapper.EqmMaintenanceMapper;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.service.EqmMaintenanceService;
import com.bikeassembly.vo.EqmMaintenanceVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import java.util.stream.Collectors;

@Service
@Slf4j
public class EqmMaintenanceServiceImpl extends ServiceImpl<EqmMaintenanceMapper, EqmMaintenance> implements EqmMaintenanceService {

    @Autowired
    private EqmMaintenanceMapper eqmMaintenanceMapper;

    @Autowired
    private BaseEquipmentMapper baseEquipmentMapper;

    @Override
    public EqmMaintenanceVO saveEqmMaintenance(EqmMaintenanceDTO dto) {
        checkEquipmentExists(dto.getEquipmentId());

        EqmMaintenance entity = toEntity(dto);
        eqmMaintenanceMapper.insert(entity);
        log.info("新增设备维护成功：maintenanceId={}", entity.getMaintenanceId());

        return toVO(entity);
    }

    @Override
    public EqmMaintenanceVO updateEqmMaintenance(Long id, EqmMaintenanceDTO dto) {
        EqmMaintenance existing = eqmMaintenanceMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }

        if (dto.getEquipmentId() != null) {
            checkEquipmentExists(dto.getEquipmentId());
        }

        // 拒绝客户端修改状态（Option A：返回业务错误）
        if (dto.getStatus() != null && !dto.getStatus().equals(existing.getStatus())) {
            throw new BaseException("保养状态不允许通过普通编辑修改，请使用专用状态操作");
        }

        EqmMaintenance entity = toEntity(dto);
        entity.setMaintenanceId(id);
        entity.setStatus(existing.getStatus());
        int rows = eqmMaintenanceMapper.updateById(entity);
        if (rows == 0) {
            throw new BaseException(MessageConstant.DATA_CONCURRENT_MODIFIED);
        }
        log.info("修改设备维护成功：maintenanceId={}", id);

        EqmMaintenance updated = eqmMaintenanceMapper.selectById(id);
        return toVO(updated);
    }

    @Override
    public EqmMaintenanceVO getEqmMaintenanceById(Long id) {
        EqmMaintenance entity = eqmMaintenanceMapper.selectById(id);
        if (entity == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        return toVO(entity);
    }

    @Override
    public PageResult<EqmMaintenanceVO> listEqmMaintenances(Long page, Long size) {
        Page<EqmMaintenance> pageParam = new Page<>(page, size);
        IPage<EqmMaintenance> pageResult = eqmMaintenanceMapper.selectPage(pageParam, null);
        List<EqmMaintenanceVO> records = pageResult.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResult.of(records, pageResult.getTotal(), pageResult.getCurrent(), pageResult.getSize());
    }

    @Override
    public void deleteEqmMaintenance(Long id) {
        EqmMaintenance existing = eqmMaintenanceMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        eqmMaintenanceMapper.deleteById(id);
        log.info("删除设备维护成功：maintenanceId={}", id);
    }

    private void checkEquipmentExists(Long equipmentId) {
        if (baseEquipmentMapper.selectById(equipmentId) == null) {
            throw new BaseException("设备不存在");
        }
    }

    private EqmMaintenance toEntity(EqmMaintenanceDTO dto) {
        EqmMaintenance entity = new EqmMaintenance();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }

    private EqmMaintenanceVO toVO(EqmMaintenance entity) {
        EqmMaintenanceVO vo = new EqmMaintenanceVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
