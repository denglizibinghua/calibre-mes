package com.bikeassembly.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.context.BaseContext;
import com.bikeassembly.dto.AndonAlertDTO;
import com.bikeassembly.dto.AndonAlertEscalateDTO;
import com.bikeassembly.constant.PermissionConstant;
import com.bikeassembly.service.PermissionService;
import com.bikeassembly.ws.WebSocketNotifier;
import com.bikeassembly.ws.WsMessageDTO;
import com.bikeassembly.dto.message.AndonMessageDTO;
import com.bikeassembly.entity.AndonAlert;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.exception.ForbiddenException;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.mapper.AndonAlertMapper;
import com.bikeassembly.mapper.BaseEquipmentMapper;
import com.bikeassembly.mapper.BaseWorkUnitMapper;
import com.bikeassembly.service.AndonAlertService;
import com.bikeassembly.service.MessageProducerService;
import com.bikeassembly.utils.ScopeValidator;
import com.bikeassembly.vo.AndonAlertVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.stream.Collectors;


@Service
@Slf4j
public class AndonAlertServiceImpl extends ServiceImpl<AndonAlertMapper, AndonAlert> implements AndonAlertService {

    @Autowired
    private AndonAlertMapper andonAlertMapper;
    @Autowired
    private BaseEquipmentMapper baseEquipmentMapper;
    @Autowired
    private BaseWorkUnitMapper baseWorkUnitMapper;
    @Autowired
    private ObjectProvider<MessageProducerService> messageProducerProvider;
    @Autowired
    private PermissionService permissionService;
    @Autowired
    private WebSocketNotifier webSocketNotifier;

    /**
     * 告警状态机：
     * 0 = 待处理 → 1 = 处理中 → 2 = 已关闭
     */
    private static final String STATUS_PENDING = "0";
    private static final String STATUS_HANDLING = "1";
    private static final String STATUS_CLOSED = "2";

    @Override
    public AndonAlertVO saveAndonAlert(AndonAlertDTO dto) {
        if (dto.getEquipmentId() != null
                && baseEquipmentMapper.selectById(dto.getEquipmentId()) == null) {
            throw new BaseException("设备不存在");
        }
        if (dto.getUnitId() != null
                && baseWorkUnitMapper.selectById(dto.getUnitId()) == null) {
            throw new BaseException("生产单元不存在");
        }

        AndonAlert entity = toEntity(dto);
        // 后端强制创建状态：忽略客户端提交的 status/handler/处理时间
        entity.setStatus(STATUS_PENDING);
        entity.setReporterUserId(BaseContext.getCurrentId());
        entity.setCreateTime(LocalDateTime.now());
        entity.setHandlerUserId(null);
        entity.setHandleTime(null);
        andonAlertMapper.insert(entity);
        log.info("新增安灯告警成功：alertId={}, status={}", entity.getAlertId(), entity.getStatus());

        notifyAndon(entity, WsMessageDTO.EVENT_CREATE);

        return toVO(entity);
    }

    @Override
    public AndonAlertVO updateAndonAlert(Long id, AndonAlertDTO dto) {
        AndonAlert existing = andonAlertMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        // 已关闭的记录不允许修改
        if (STATUS_CLOSED.equals(existing.getStatus())) {
            throw new BaseException("已关闭的告警不可修改");
        }

        if (dto.getEquipmentId() != null
                && baseEquipmentMapper.selectById(dto.getEquipmentId()) == null) {
            throw new BaseException("设备不存在");
        }
        if (dto.getUnitId() != null
                && baseWorkUnitMapper.selectById(dto.getUnitId()) == null) {
            throw new BaseException("生产单元不存在");
        }

        // 拒绝客户端修改受保护字段（Option A：返回业务错误）
        if (dto.getStatus() != null && !dto.getStatus().equals(existing.getStatus())) {
            throw new BaseException("状态不允许通过普通编辑修改，请使用受理/关闭操作");
        }
        if (dto.getHandlerUserId() != null && !dto.getHandlerUserId().equals(existing.getHandlerUserId())) {
            throw new BaseException("处理人ID不允许修改");
        }
        if (dto.getHandleTime() != null) {
            throw new BaseException("处理时间不允许修改");
        }
        if (dto.getReporterUserId() != null && !dto.getReporterUserId().equals(existing.getReporterUserId())) {
            throw new BaseException("上报人ID不允许修改");
        }
        if (dto.getCreateTime() != null) {
            throw new BaseException("告警时间不允许修改");
        }
        if (dto.getAlertNo() != null && !dto.getAlertNo().equals(existing.getAlertNo())) {
            throw new BaseException("告警编号不允许修改");
        }

        AndonAlert entity = toEntity(dto);
        entity.setAlertId(id);
        entity.setStatus(existing.getStatus());
        entity.setHandlerUserId(existing.getHandlerUserId());
        entity.setHandleTime(existing.getHandleTime());
        entity.setReporterUserId(existing.getReporterUserId());
        entity.setCreateTime(existing.getCreateTime());
        entity.setAlertNo(existing.getAlertNo());
        int rows = andonAlertMapper.updateById(entity);
        if (rows == 0) {
            throw new BaseException(MessageConstant.DATA_CONCURRENT_MODIFIED);
        }
        log.info("修改安灯告警成功：alertId={}", id);

        AndonAlert updated = andonAlertMapper.selectById(id);
        return toVO(updated);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AndonAlertVO acceptAndonAlert(Long id) {
        AndonAlert existing = andonAlertMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }

        if (STATUS_CLOSED.equals(existing.getStatus())) {
            throw new BaseException("已关闭的告警不可再次受理");
        }
        if (STATUS_HANDLING.equals(existing.getStatus()) && existing.getHandlerUserId() != null) {
            throw new BaseException("该告警已被受理，请勿重复操作");
        }

        Long currentUserId = BaseContext.getCurrentId();

        // 并发安全：条件更新 WHERE id=? AND status='0' AND handler_user_id IS NULL
        LambdaUpdateWrapper<AndonAlert> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AndonAlert::getAlertId, id)
               .eq(AndonAlert::getStatus, STATUS_PENDING)
               .isNull(AndonAlert::getHandlerUserId)
               .set(AndonAlert::getStatus, STATUS_HANDLING)
               .set(AndonAlert::getHandlerUserId, currentUserId)
               .set(AndonAlert::getHandleTime, LocalDateTime.now());
        int rows = andonAlertMapper.update(null, wrapper);
        if (rows == 0) {
            throw new BaseException("受理失败：该告警已被其他处理人员受理或状态已变更，请刷新后重试");
        }
        log.info("受理告警成功：alertId={}, handlerUserId={}", id, currentUserId);

        AndonAlert updated = andonAlertMapper.selectById(id);
        notifyAndon(updated, WsMessageDTO.EVENT_ACCEPT);
        return toVO(updated);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AndonAlertVO closeAndonAlert(Long id) {
        AndonAlert existing = andonAlertMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }

        if (!STATUS_HANDLING.equals(existing.getStatus())) {
            if (STATUS_CLOSED.equals(existing.getStatus())) {
                throw new BaseException("该告警已关闭，请勿重复操作");
            }
            if (STATUS_PENDING.equals(existing.getStatus())) {
                throw new BaseException("待处理的告警请先受理");
            }
            throw new BaseException("当前状态不允许关闭操作");
        }

        Long currentUserId = BaseContext.getCurrentId();
        if (existing.getHandlerUserId() != null && !existing.getHandlerUserId().equals(currentUserId)) {
            throw new BaseException("只能关闭自己受理的告警");
        }

        // 并发安全：条件更新 WHERE id=? AND status='1' AND handler_user_id=?
        LambdaUpdateWrapper<AndonAlert> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AndonAlert::getAlertId, id)
               .eq(AndonAlert::getStatus, STATUS_HANDLING)
               .eq(AndonAlert::getHandlerUserId, currentUserId)
               .set(AndonAlert::getStatus, STATUS_CLOSED);
        int rows = andonAlertMapper.update(null, wrapper);
        if (rows == 0) {
            throw new BaseException("关闭失败：告警状态已变更，请刷新后重试");
        }
        log.info("关闭告警成功：alertId={}, handlerUserId={}", id, currentUserId);

        AndonAlert updated = andonAlertMapper.selectById(id);
        notifyAndon(updated, WsMessageDTO.EVENT_CLOSE);
        return toVO(updated);
    }

    @Override
    public AndonAlertVO getAndonAlertById(Long id) {
        AndonAlert entity = andonAlertMapper.selectById(id);
        if (entity == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        return toVO(entity);
    }

    @Override
    public PageResult<AndonAlertVO> listAndonAlerts(Long page, Long size, String scope, String relation) {
        // Service 层最终安全边界：重新校验 scope 合法性，防止 Controller 被绕过
        scope = ScopeValidator.requireValid(scope, ScopeValidator.MINE_OR_ALL);
        Page<AndonAlert> pageParam = new Page<>(page, size);
        IPage<AndonAlert> pageResult;

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
            pageResult = andonAlertMapper.selectPageByCondition(pageParam, reporterUserId, handlerUserId, isPending);
        } else {
            // scope=all（管理员）且无 relation 筛选：查询全部（Controller 已校验 all 权限）
            pageResult = andonAlertMapper.selectPage(pageParam, null);
        }

        List<AndonAlertVO> records = pageResult.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResult.of(records, pageResult.getTotal(), pageResult.getCurrent(), pageResult.getSize());
    }

    @Override
    public void deleteAndonAlert(Long id) {
        AndonAlert existing = andonAlertMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        andonAlertMapper.deleteById(id);
        log.info("删除安灯告警成功：alertId={}", id);
    }

    @Override
    public AndonAlertVO escalateAndonAlert(Long id, AndonAlertEscalateDTO dto) {
        AndonAlert existing = andonAlertMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        if (STATUS_CLOSED.equals(existing.getStatus())) {
            throw new BaseException("已关闭的告警不可升级");
        }
        if ("1".equals(existing.getEscalationStatus())) {
            throw new BaseException("该告警已升级，请勿重复操作");
        }

        Long currentUserId = BaseContext.getCurrentId();
        boolean isReporter = existing.getReporterUserId() != null && existing.getReporterUserId().equals(currentUserId);
        boolean isHandler = existing.getHandlerUserId() != null && existing.getHandlerUserId().equals(currentUserId);
        boolean hasAllScope = "ALL".equals(BaseContext.getCurrentDataScope());
        if (!isReporter && !isHandler && !hasAllScope) {
            throw new BaseException("只能升级本人上报或受理的告警");
        }

        String remark = dto != null ? dto.getRemark() : null;
        // 并发安全：条件更新 WHERE alert_id=? AND status=? AND escalation_status='0'
        LambdaUpdateWrapper<AndonAlert> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AndonAlert::getAlertId, id)
               .eq(AndonAlert::getStatus, existing.getStatus())
               .eq(AndonAlert::getEscalationStatus, "0")
               .set(AndonAlert::getEscalationStatus, "1")
               .set(AndonAlert::getEscalatedBy, currentUserId)
               .set(AndonAlert::getEscalationTime, LocalDateTime.now())
               .set(AndonAlert::getEscalationRemark, remark);
        int rows = andonAlertMapper.update(null, wrapper);
        if (rows == 0) {
            throw new BaseException("升级失败：告警状态已变更或已升级，请刷新后重试");
        }
        log.info("升级告警成功：alertId={}, escalatedBy={}", id, currentUserId);

        AndonAlert updated = andonAlertMapper.selectById(id);
        notifyAndon(updated, WsMessageDTO.EVENT_ESCALATE);
        return toVO(updated);
    }

    /**
     * 安灯状态变更通知：RabbitMQ 启用时发送 MQ（由消费者异步推送 WebSocket），
     * 未启用时降级为同步推送：管理端全量 + 上报人/处理人（app 端）。
     */
    private void notifyAndon(AndonAlert entity, String eventType) {
        com.bikeassembly.utils.AfterCommitNotification.execute(() -> sendAndonNotification(entity, eventType));
    }

    private void sendAndonNotification(AndonAlert entity, String eventType) {
        MessageProducerService producer = messageProducerProvider == null
                ? null : messageProducerProvider.getIfAvailable();
        if (producer != null) {
            try {
                AndonMessageDTO message = AndonMessageDTO.builder()
                        .alertId(entity.getAlertId())
                        .alertType(entity.getAlertType())
                        .level(entity.getAlertLevel())
                        .eventType(eventType)
                        .createTime(entity.getCreateTime())
                        .build();
                producer.sendAndonMessage(message);
                return;
            } catch (Exception e) {
                log.warn("安灯消息发送失败（不影响主业务）：alertId={}, error={}",
                        entity.getAlertId(), e.getMessage(), e);
            }
        }
        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("alertId", entity.getAlertId());
        data.put("alertNo", entity.getAlertNo());
        data.put("alertType", entity.getAlertType());
        data.put("alertLevel", entity.getAlertLevel());
        data.put("status", entity.getStatus());
        data.put("workOrderId", entity.getWorkOrderId());
        data.put("unitId", entity.getUnitId());
        data.put("equipmentId", entity.getEquipmentId());
        data.put("reporterUserId", entity.getReporterUserId());
        data.put("handlerUserId", entity.getHandlerUserId());
        data.put("escalationStatus", entity.getEscalationStatus());
        webSocketNotifier.pushToAdmin(WsMessageDTO.BIZ_ANDON, eventType, data);
        if (entity.getReporterUserId() != null) {
            webSocketNotifier.pushToUser(entity.getReporterUserId(), WsMessageDTO.BIZ_ANDON, eventType, data);
        }
        if (entity.getHandlerUserId() != null) {
            webSocketNotifier.pushToUser(entity.getHandlerUserId(), WsMessageDTO.BIZ_ANDON, eventType, data);
        }
    }

    private AndonAlert toEntity(AndonAlertDTO dto) {
        AndonAlert entity = new AndonAlert();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }

    private AndonAlertVO toVO(AndonAlert entity) {
        AndonAlertVO vo = new AndonAlertVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
