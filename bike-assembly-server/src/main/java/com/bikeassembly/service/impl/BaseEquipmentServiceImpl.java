package com.bikeassembly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.dto.BaseEquipmentDTO;
import com.bikeassembly.dto.message.EquipmentMessageDTO;
import com.bikeassembly.entity.BaseEquipment;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.mapper.BaseEquipmentMapper;
import com.bikeassembly.mapper.BaseWorkUnitMapper;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.service.BaseEquipmentService;
import com.bikeassembly.service.MessageProducerService;
import com.bikeassembly.vo.BaseEquipmentVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import java.util.List;

import java.util.stream.Collectors;

@Service
@Slf4j
public class BaseEquipmentServiceImpl extends ServiceImpl<BaseEquipmentMapper, BaseEquipment> implements BaseEquipmentService {

    @Autowired
    private BaseEquipmentMapper baseEquipmentMapper;

    @Autowired
    private BaseWorkUnitMapper baseWorkUnitMapper;
    @Autowired
    private ObjectProvider<MessageProducerService> messageProducerProvider;

    @Override
    public BaseEquipmentVO saveEquipment(BaseEquipmentDTO dto) {
        checkEquipmentCodeUnique(dto.getEquipmentCode(), null);
        if (dto.getUnitId() != null) {
            checkWorkUnitExists(dto.getUnitId());
        }

        BaseEquipment entity = toEntity(dto);
        baseEquipmentMapper.insert(entity);
        log.info("新增设备成功：equipmentId={}, equipmentCode={}", entity.getEquipmentId(), entity.getEquipmentCode());

        // 发送 RabbitMQ 设备消息（RabbitMQ 未启用时优雅降级）
        sendEquipmentMessageSafely(entity);

        return toVO(entity);
    }

    @Override
    public BaseEquipmentVO updateEquipment(Long id, BaseEquipmentDTO dto) {
        BaseEquipment existing = baseEquipmentMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }

        checkEquipmentCodeUnique(dto.getEquipmentCode(), id);
        if (dto.getUnitId() != null) {
            checkWorkUnitExists(dto.getUnitId());
        }

        // 运行状态只能由维修/保养专用业务动作改变，普通编辑不可修改
        if (dto.getRunningStatus() != null && !dto.getRunningStatus().equals(existing.getRunningStatus())) {
            throw new BaseException("运行状态不允许通过普通编辑修改");
        }
        BaseEquipment entity = toEntity(dto);
        entity.setEquipmentId(id);
        entity.setRunningStatus(existing.getRunningStatus());
        baseEquipmentMapper.updateById(entity);
        log.info("修改设备成功：equipmentId={}", id);

        BaseEquipment updated = baseEquipmentMapper.selectById(id);
        return toVO(updated);
    }

    @Override
    public BaseEquipmentVO getEquipmentById(Long id) {
        BaseEquipment entity = baseEquipmentMapper.selectById(id);
        if (entity == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        return toVO(entity);
    }

    @Override
    public PageResult<BaseEquipmentVO> listEquipments(Long page, Long size, String equipmentCode, String equipmentName) {
        LambdaQueryWrapper<BaseEquipment> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(equipmentCode != null && !equipmentCode.isBlank(), BaseEquipment::getEquipmentCode, equipmentCode)
               .like(equipmentName != null && !equipmentName.isBlank(), BaseEquipment::getEquipmentName, equipmentName);
        Page<BaseEquipment> pageParam = new Page<>(page, size);
        IPage<BaseEquipment> pageResult = baseEquipmentMapper.selectPage(pageParam, wrapper);
        List<BaseEquipmentVO> records = pageResult.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResult.of(records, pageResult.getTotal(), pageResult.getCurrent(), pageResult.getSize());
    }

    @Override
    public void deleteEquipment(Long id) {
        BaseEquipment existing = baseEquipmentMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }

        int refCount = baseEquipmentMapper.countReferences(id);
        if (refCount > 0) {
            throw new BaseException("该设备被 " + refCount + " 条业务数据引用，无法删除");
        }

        baseEquipmentMapper.deleteById(id);
        log.info("删除设备成功：equipmentId={}", id);
    }

    private void checkEquipmentCodeUnique(String equipmentCode, Long excludeId) {
        LambdaQueryWrapper<BaseEquipment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BaseEquipment::getEquipmentCode, equipmentCode);
        if (excludeId != null) {
            wrapper.ne(BaseEquipment::getEquipmentId, excludeId);
        }
        if (baseEquipmentMapper.selectCount(wrapper) > 0) {
            throw new BaseException("设备编码已存在：" + equipmentCode);
        }
    }

    private void checkWorkUnitExists(Long unitId) {
        if (baseWorkUnitMapper.selectById(unitId) == null) {
            throw new BaseException("工作单元不存在");
        }
    }

    private BaseEquipment toEntity(BaseEquipmentDTO dto) {
        BaseEquipment entity = new BaseEquipment();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }

    private BaseEquipmentVO toVO(BaseEquipment entity) {
        BaseEquipmentVO vo = new BaseEquipmentVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    /**
     * 安全发送设备创建消息。
     * <p>
     * 当 RabbitMQ 未启用（{@code app.rabbitmq.enabled=false}）时，
     * {@code MessageProducerService} Bean 不存在，通过 {@code ObjectProvider} 优雅降级。
     * 发送失败不影响主业务创建结果。
     */
    private void sendEquipmentMessageSafely(BaseEquipment entity) {
        try {
            MessageProducerService producer = messageProducerProvider.getIfAvailable();
            if (producer == null) {
                log.debug("RabbitMQ 未启用，跳过设备消息发送：equipmentId={}, equipmentCode={}",
                        entity.getEquipmentId(), entity.getEquipmentCode());
                return;
            }
            EquipmentMessageDTO message = EquipmentMessageDTO.builder()
                    .equipmentId(entity.getEquipmentId())
                    .equipmentCode(entity.getEquipmentCode())
                    .eventType("EQUIPMENT_CREATED")
                    .createTime(entity.getCreateTime() != null
                            ? entity.getCreateTime() : LocalDateTime.now())
                    .build();
            producer.sendEquipmentMessage(message);
        } catch (Exception e) {
            log.warn("设备消息发送失败（不影响主业务）：equipmentId={}, equipmentCode={}, error={}",
                    entity.getEquipmentId(), entity.getEquipmentCode(), e.getMessage(), e);
        }
    }
}
