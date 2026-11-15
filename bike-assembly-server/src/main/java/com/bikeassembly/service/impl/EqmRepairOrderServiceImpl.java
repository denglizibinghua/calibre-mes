package com.bikeassembly.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.constant.PermissionConstant;
import com.bikeassembly.context.BaseContext;
import com.bikeassembly.dto.EqmRepairOrderDTO;
import com.bikeassembly.dto.message.EquipmentMessageDTO;
import com.bikeassembly.entity.BaseEquipment;
import com.bikeassembly.entity.EqmMaintenance;
import com.bikeassembly.entity.EqmRepairOrder;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.exception.ForbiddenException;
import com.bikeassembly.mapper.BaseEquipmentMapper;
import com.bikeassembly.mapper.EqmRepairOrderMapper;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.service.EqmRepairOrderService;
import com.bikeassembly.service.MessageProducerService;
import com.bikeassembly.service.PermissionService;
import com.bikeassembly.ws.WebSocketNotifier;
import com.bikeassembly.ws.WsMessageDTO;
import com.bikeassembly.utils.ScopeValidator;
import com.bikeassembly.vo.EqmRepairOrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EqmRepairOrderServiceImpl extends ServiceImpl<EqmRepairOrderMapper, EqmRepairOrder> implements EqmRepairOrderService {

    @Autowired
    private EqmRepairOrderMapper eqmRepairOrderMapper;
    @Autowired
    private BaseEquipmentMapper baseEquipmentMapper;
    @Autowired
    private com.bikeassembly.mapper.EqmMaintenanceMapper eqmMaintenanceMapper;
    @Autowired
    private PermissionService permissionService;
    @Autowired
    private WebSocketNotifier webSocketNotifier;
    @Autowired
    private ObjectProvider<MessageProducerService> messageProducerProvider;

    /**
     * 维修状态机：
     * 0 = 待受理 → 1 = 维修中 → 2 = 已完成
     */
    private static final String STATUS_PENDING = "0";
    private static final String STATUS_REPAIRING = "1";
    private static final String STATUS_COMPLETED = "2";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EqmRepairOrderVO saveEqmRepairOrder(EqmRepairOrderDTO dto) {
        // 锁定设备行（统一加锁顺序：先锁设备，再操作维修单）
        BaseEquipment equipment = baseEquipmentMapper.selectByIdForUpdate(dto.getEquipmentId());
        if (equipment == null) {
            throw new BaseException("设备不存在");
        }

        EqmRepairOrder entity = toEntity(dto);
        entity.setStatus(STATUS_PENDING);
        entity.setReporterUserId(BaseContext.getCurrentId());
        entity.setReportTime(LocalDateTime.now());
        entity.setHandlerUserId(null);
        entity.setFinishTime(null);
        entity.setResultDesc(null);
        eqmRepairOrderMapper.insert(entity);

        // 设备进入不可运行状态（仅当当前为可运行时更新）
        if (!"1".equals(equipment.getRunningStatus())) {
            setEquipmentRunningStatus(dto.getEquipmentId(), "1");
        }
        log.info("新增设备维修工单成功：repairId={}", entity.getRepairId());

        return toVO(entity);
    }

    @Override
    public EqmRepairOrderVO updateEqmRepairOrder(Long id, EqmRepairOrderDTO dto) {
        EqmRepairOrder existing = eqmRepairOrderMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        // 已完成记录不允许修改
        if (STATUS_COMPLETED.equals(existing.getStatus())) {
            throw new BaseException("已完成的维修单不可修改");
        }

        if (dto.getEquipmentId() != null) {
            checkEquipmentExists(dto.getEquipmentId());
        }

        // 拒绝客户端修改受保护字段（Option A：返回业务错误）
        if (dto.getStatus() != null && !dto.getStatus().equals(existing.getStatus())) {
            throw new BaseException("状态不允许通过普通编辑修改，请使用接单/完成操作");
        }
        if (dto.getHandlerUserId() != null && !dto.getHandlerUserId().equals(existing.getHandlerUserId())) {
            throw new BaseException("维修人ID不允许修改");
        }
        if (dto.getFinishTime() != null && !dto.getFinishTime().equals(existing.getFinishTime())) {
            throw new BaseException("完成时间不允许修改");
        }
        if (dto.getResultDesc() != null && !dto.getResultDesc().equals(existing.getResultDesc())) {
            throw new BaseException("维修结果不允许通过普通编辑修改，请使用完成操作");
        }
        if (dto.getReporterUserId() != null && !dto.getReporterUserId().equals(existing.getReporterUserId())) {
            throw new BaseException("报修人ID不允许修改");
        }
        if (dto.getReportTime() != null) {
            throw new BaseException("报修时间不允许修改");
        }

        EqmRepairOrder entity = toEntity(dto);
        entity.setRepairId(id);
        entity.setStatus(existing.getStatus());
        entity.setHandlerUserId(existing.getHandlerUserId());
        entity.setFinishTime(existing.getFinishTime());
        entity.setResultDesc(existing.getResultDesc());
        entity.setReporterUserId(existing.getReporterUserId());
        entity.setReportTime(existing.getReportTime());
        int rows = eqmRepairOrderMapper.updateById(entity);
        if (rows == 0) {
            throw new BaseException(MessageConstant.DATA_CONCURRENT_MODIFIED);
        }
        log.info("修改设备维修工单成功：repairId={}", id);

        EqmRepairOrder updated = eqmRepairOrderMapper.selectById(id);
        return toVO(updated);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EqmRepairOrderVO acceptEqmRepairOrder(Long id) {
        // 先验证记录存在
        EqmRepairOrder existing = eqmRepairOrderMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }

        if (STATUS_COMPLETED.equals(existing.getStatus())) {
            throw new BaseException("已完成的维修单不可再次受理");
        }
        if (STATUS_REPAIRING.equals(existing.getStatus()) && existing.getHandlerUserId() != null) {
            throw new BaseException("该维修单已被受理，请勿重复操作");
        }

        Long currentUserId = BaseContext.getCurrentId();

        // 并发安全：条件更新 WHERE id=? AND status='0' AND handler_user_id IS NULL
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<EqmRepairOrder> wrapper = new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        wrapper.eq("repair_id", id)
               .eq("status", STATUS_PENDING)
               .isNull("handler_user_id")
               .set("status", STATUS_REPAIRING)
               .set("handler_user_id", currentUserId);
        int rows = eqmRepairOrderMapper.update(null, wrapper);
        if (rows == 0) {
            throw new BaseException("接单失败：该维修单已被其他维修员接单或状态已变更，请刷新后重试");
        }
        log.info("受理维修单成功：repairId={}, handlerUserId={}", id, currentUserId);

        EqmRepairOrder updated = eqmRepairOrderMapper.selectById(id);
        notifyRepair(updated, WsMessageDTO.EVENT_ACCEPT);
        return toVO(updated);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EqmRepairOrderVO completeEqmRepairOrder(Long id) {
        // 无DTO调用默认为空结果（向后兼容管理端）
        return completeEqmRepairOrderWithDto(id, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EqmRepairOrderVO completeEqmRepairOrderWithDto(Long id, com.bikeassembly.dto.CompleteRepairDTO dto) {
        EqmRepairOrder existing = eqmRepairOrderMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }

        if (!STATUS_REPAIRING.equals(existing.getStatus())) {
            if (STATUS_COMPLETED.equals(existing.getStatus())) {
                throw new BaseException("该维修单已完成，请勿重复操作");
            }
            if (STATUS_PENDING.equals(existing.getStatus())) {
                throw new BaseException("待受理的维修单请先受理");
            }
            throw new BaseException("当前状态不允许完成操作");
        }

        Long currentUserId = BaseContext.getCurrentId();
        if (existing.getHandlerUserId() != null && !existing.getHandlerUserId().equals(currentUserId)) {
            throw new BaseException("只能完成自己受理的维修单");
        }

        // 锁定设备行（统一加锁顺序：先锁设备，再操作维修单）
        BaseEquipment equipment = baseEquipmentMapper.selectByIdForUpdate(existing.getEquipmentId());
        if (equipment == null) {
            throw new BaseException("关联设备不存在");
        }

        String resultDesc = dto != null ? dto.getResultDesc() : null;

        // 并发安全：条件更新 WHERE repair_id=? AND status='1' AND handler_user_id=?
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<EqmRepairOrder> wrapper =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        wrapper.eq("repair_id", id)
               .eq("status", STATUS_REPAIRING)
               .eq("handler_user_id", currentUserId)
               .set("status", STATUS_COMPLETED)
               .set("finish_time", LocalDateTime.now());
        if (resultDesc != null && !resultDesc.isBlank()) {
            wrapper.set("result_desc", resultDesc);
        }
        int rows = eqmRepairOrderMapper.update(null, wrapper);
        if (rows == 0) {
            throw new BaseException("完成失败：维修单状态已变更，请刷新后重试");
        }
        log.info("完成维修单成功：repairId={}, userId={}", id, currentUserId);

        // 设备状态联动：检查同设备是否还有其他活动维修/维护任务
        tryRestoreEquipmentStatus(existing.getEquipmentId());

        EqmRepairOrder updated = eqmRepairOrderMapper.selectById(id);
        notifyRepair(updated, WsMessageDTO.EVENT_COMPLETE);
        return toVO(updated);
    }

    /**
     * 维修状态变更通知：RabbitMQ 启用时发送 MQ（由消费者异步推送 WebSocket），
     * 未启用时降级为同步推送：管理端全量 + 上报人/处理人（app 端）。
     */
    private void notifyRepair(EqmRepairOrder entity, String eventType) {
        com.bikeassembly.utils.AfterCommitNotification.execute(() -> sendRepairNotification(entity, eventType));
    }

    private void sendRepairNotification(EqmRepairOrder entity, String eventType) {
        MessageProducerService producer = messageProducerProvider == null
                ? null : messageProducerProvider.getIfAvailable();
        if (producer != null) {
            try {
                EquipmentMessageDTO message = EquipmentMessageDTO.builder()
                        .equipmentId(entity.getEquipmentId())
                        .repairId(entity.getRepairId())
                        .status(entity.getStatus())
                        .eventType(eventType)
                        .createTime(LocalDateTime.now())
                        .build();
                producer.sendEquipmentMessage(message);
                return;
            } catch (Exception e) {
                log.warn("维修消息发送失败（不影响主业务）：repairId={}, error={}",
                        entity.getRepairId(), e.getMessage(), e);
            }
        }
        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("repairId", entity.getRepairId());
        data.put("equipmentId", entity.getEquipmentId());
        data.put("status", entity.getStatus());
        data.put("reporterUserId", entity.getReporterUserId());
        data.put("handlerUserId", entity.getHandlerUserId());
        webSocketNotifier.pushToAdmin(WsMessageDTO.BIZ_REPAIR, eventType, data);
        if (entity.getReporterUserId() != null) {
            webSocketNotifier.pushToUser(entity.getReporterUserId(), WsMessageDTO.BIZ_REPAIR, eventType, data);
        }
        if (entity.getHandlerUserId() != null) {
            webSocketNotifier.pushToUser(entity.getHandlerUserId(), WsMessageDTO.BIZ_REPAIR, eventType, data);
        }
    }

    /**
     * 检查设备是否可恢复正常运行状态。
     * 条件：无其他活动维修单（待维修/维修中）、无进行中保养、设备未停用/报废。
     */
    private void tryRestoreEquipmentStatus(Long equipmentId) {
        BaseEquipment equipment = baseEquipmentMapper.selectById(equipmentId);
        if (equipment == null) return;

        // 设备已停用(1) → 不恢复
        if ("1".equals(equipment.getStatus())) {
            log.info("设备已停用，不恢复运行状态：equipmentId={}", equipmentId);
            return;
        }

        // 检查是否还有其他活动维修单
        Long activeRepairs = eqmRepairOrderMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<EqmRepairOrder>()
                        .eq("equipment_id", equipmentId)
                        .in("status", STATUS_PENDING, STATUS_REPAIRING));
        if (activeRepairs != null && activeRepairs > 0) {
            log.info("设备仍有活动维修单，保持故障状态：equipmentId={}, activeCount={}",
                    equipmentId, activeRepairs);
            return;
        }

        // 检查是否有进行中保养
        Long activeMaintenance = eqmMaintenanceMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<EqmMaintenance>()
                        .eq("equipment_id", equipmentId)
                        .eq("status", "1"));
        if (activeMaintenance != null && activeMaintenance > 0) {
            log.info("设备有进行中保养，不恢复运行状态：equipmentId={}", equipmentId);
            return;
        }

        // 条件更新设备运行状态为正常
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<BaseEquipment> eqWrapper =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        eqWrapper.eq("equipment_id", equipmentId).set("running_status", "0");
        int eqRows = baseEquipmentMapper.update(null, eqWrapper);
        if (eqRows > 0) {
            log.info("设备运行状态恢复正常：equipmentId={}", equipmentId);
        }
    }

    /** 设置设备运行状态（报修时设为故障） */
    private void setEquipmentRunningStatus(Long equipmentId, String runningStatus) {
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<BaseEquipment> wrapper = new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        wrapper.eq("equipment_id", equipmentId)
               .set("running_status", runningStatus);
        baseEquipmentMapper.update(null, wrapper);
    }

    @Override
    public EqmRepairOrderVO getEqmRepairOrderById(Long id) {
        EqmRepairOrder entity = eqmRepairOrderMapper.selectById(id);
        if (entity == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        return toVO(entity);
    }

    @Override
    public PageResult<EqmRepairOrderVO> listEqmRepairOrders(Long page, Long size, String scope, String relation) {
        // Service 层最终安全边界：重新校验 scope 合法性，防止 Controller 被绕过
        scope = ScopeValidator.requireValid(scope, ScopeValidator.MINE_OR_ALL);
        Page<EqmRepairOrder> pageParam = new Page<>(page, size);
        IPage<EqmRepairOrder> pageResult;

        Long reporterUserId = null;
        Long handlerUserId = null;
        Boolean isPending = null;

        // dataScope=ALL 的用户（管理员等），mine 等同于 all
        boolean isMine = "mine".equals(scope) && !"ALL".equals(BaseContext.getCurrentDataScope());

        if ("pending".equals(relation)) {
            isPending = true;
        } else if ("handled".equals(relation)) {
            handlerUserId = BaseContext.getCurrentId();
        } else if (isMine) {
            reporterUserId = BaseContext.getCurrentId();
        }

        boolean hasFilter = reporterUserId != null || handlerUserId != null || isPending != null;

        if (hasFilter) {
            pageResult = eqmRepairOrderMapper.selectPageByCondition(pageParam, reporterUserId, handlerUserId, isPending);
        } else {
            // scope=all（管理员）且无 relation 筛选：查询全部（Controller 已校验 all 权限）
            pageResult = eqmRepairOrderMapper.selectPage(pageParam, null);
        }

        List<EqmRepairOrderVO> records = pageResult.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResult.of(records, pageResult.getTotal(), pageResult.getCurrent(), pageResult.getSize());
    }

    @Override
    public void deleteEqmRepairOrder(Long id) {
        EqmRepairOrder existing = eqmRepairOrderMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        eqmRepairOrderMapper.deleteById(id);
        log.info("删除设备维修工单成功：repairId={}", id);
    }

    private void checkEquipmentExists(Long equipmentId) {
        if (baseEquipmentMapper.selectById(equipmentId) == null) {
            throw new BaseException("设备不存在");
        }
    }

    private EqmRepairOrder toEntity(EqmRepairOrderDTO dto) {
        EqmRepairOrder entity = new EqmRepairOrder();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }

    private EqmRepairOrderVO toVO(EqmRepairOrder entity) {
        EqmRepairOrderVO vo = new EqmRepairOrderVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
