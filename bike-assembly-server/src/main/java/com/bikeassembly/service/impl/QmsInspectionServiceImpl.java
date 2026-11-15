package com.bikeassembly.service.impl;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.context.BaseContext;
import com.bikeassembly.dto.QmsInspectionDTO;
import com.bikeassembly.dto.message.QualityMessageDTO;
import com.bikeassembly.entity.ProWorkOrder;
import com.bikeassembly.entity.ProWorkProcess;
import com.bikeassembly.entity.QmsInspection;
import com.bikeassembly.enums.InspectionStatus;
import com.bikeassembly.enums.WorkProcessStatus;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.exception.StateMachineException;
import com.bikeassembly.mapper.ProWorkOrderMapper;
import com.bikeassembly.mapper.ProWorkProcessMapper;
import com.bikeassembly.mapper.QmsInspectionMapper;
import com.bikeassembly.constant.PermissionConstant;
import com.bikeassembly.service.IdempotencyService;
import com.bikeassembly.service.IdempotentResult;
import com.bikeassembly.service.MessageProducerService;
import com.bikeassembly.service.PermissionService;
import com.bikeassembly.ws.WebSocketNotifier;
import com.bikeassembly.ws.WsMessageDTO;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.service.ProWorkProcessService;
import com.bikeassembly.service.QmsInspectionService;
import com.bikeassembly.utils.ScopeValidator;
import com.bikeassembly.vo.QmsInspectionVO;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class QmsInspectionServiceImpl extends ServiceImpl<QmsInspectionMapper, QmsInspection> implements QmsInspectionService {

    @Autowired
    private QmsInspectionMapper qmsInspectionMapper;
    @Autowired
    private ProWorkOrderMapper proWorkOrderMapper;
    @Autowired
    private ProWorkProcessMapper proWorkProcessMapper;
    @Autowired
    private ProWorkProcessService proWorkProcessService;
    @Autowired
    private PermissionService permissionService;
    @Autowired
    private WebSocketNotifier webSocketNotifier;
    @Autowired
    private IdempotencyService idempotencyService;
    @Autowired
    private com.bikeassembly.util.UserContextHelper userContextHelper;
    @Autowired
    private ObjectProvider<MessageProducerService> messageProducerProvider;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 录入检验结果：更新已有待检质检单，status: 0(待检) → 1(已录入)。
     * 不允许创建新记录，只更新已有 inspectionId 的 result/detailsJson/inspector。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public QmsInspectionVO enterInspectionResult(Long id, QmsInspectionDTO dto) {
        // [幂等保护]
        java.util.LinkedHashMap<String, Object> idmParams = new java.util.LinkedHashMap<>();
        idmParams.put("inspectionId", id);
        idmParams.put("result", dto.getResult());
        idmParams.put("detailsJson", dto.getDetailsJson());
        QmsInspectionVO cachedVo = checkInspectionIdempotency(
                IdempotencyService.ACTION_ENTER_RESULT, idmParams, id);
        if (cachedVo != null) {
            return cachedVo;
        }

        try {
            QmsInspectionVO result = doEnterInspectionResult(id, dto);
            markInspectionIdempotencySuccess(result);
            return result;
        } catch (Exception e) {
            markInspectionIdempotencyFailure();
            throw e;
        }
    }

    private QmsInspectionVO doEnterInspectionResult(Long id, QmsInspectionDTO dto) {
        QmsInspection existing = qmsInspectionMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }

        InspectionStatus currentStatus = InspectionStatus.fromCode(existing.getStatus());
        if (currentStatus != InspectionStatus.PENDING) {
            if (currentStatus == InspectionStatus.ENTERED) {
                throw new BaseException("该质检单已录入，请确认结果或联系管理员");
            }
            if (currentStatus == InspectionStatus.CONFIRMED) {
                throw new BaseException("该质检单已确认，不可修改");
            }
            throw new BaseException("当前状态不允许录入检验结果");
        }

        // 归属校验：已分配给其他质检员的不能录入
        Long currentUserId = BaseContext.getCurrentId();
        if (existing.getInspectorUserId() != null
                && !existing.getInspectorUserId().equals(currentUserId)) {
            throw new BaseException("该质检单已分配给其他质检员");
        }

        // 原子领取：未分配任务 → 首次录入时原子绑定当前质检员
        if (existing.getInspectorUserId() == null) {
            LambdaUpdateWrapper<QmsInspection> claimWrapper = new LambdaUpdateWrapper<>();
            claimWrapper.eq(QmsInspection::getInspectionId, id)
                       .eq(QmsInspection::getStatus, InspectionStatus.PENDING.getCode())
                       .isNull(QmsInspection::getInspectorUserId)
                       .eq(QmsInspection::getVersion, existing.getVersion())
                       .set(QmsInspection::getInspectorUserId, currentUserId)
                       .set(QmsInspection::getInspector, dto.getInspector())
                       .setSql("version = version + 1");
            int claimRows = qmsInspectionMapper.update(null, claimWrapper);
            if (claimRows == 0) {
                throw new BaseException("该质检单已被其他质检员领取，请刷新后重试");
            }
            // 重新读取最新版本号
            existing = qmsInspectionMapper.selectById(id);
        }

        // 校验质检单绑定了工序执行记录
        if (existing.getProcessRecordId() == null) {
            throw new BaseException("质检单未绑定工序执行记录，无法录入结果");
        }

        // 校验关联工序存在且状态为待检
        ProWorkProcess linkedProcess = proWorkProcessMapper.selectById(existing.getProcessRecordId());
        if (linkedProcess == null) {
            throw new BaseException("未找到质检单关联的工序记录：processRecordId=" + existing.getProcessRecordId());
        }
        if (!WorkProcessStatus.WAIT_INSPECTION.getCode().equals(linkedProcess.getStatus())) {
            throw new BaseException(String.format("关联工序[%d]当前状态为[%s]，不是待检状态，无法录入检验结果",
                    linkedProcess.getProcessRecordId(),
                    WorkProcessStatus.fromCode(linkedProcess.getStatus()).getLabel()));
        }
        if (!existing.getWorkOrderId().equals(linkedProcess.getWorkOrderId())) {
            throw new BaseException("质检单与关联工序的工单不一致");
        }
        if (existing.getProcessId() != null
                && !existing.getProcessId().equals(linkedProcess.getProcessId())) {
            throw new BaseException("质检单与关联工序的工序ID不一致");
        }

        // 校验检验结果
        if (dto.getResult() == null || dto.getResult().isBlank()) {
            throw new BaseException("检验结果不能为空");
        }
        String result = dto.getResult();
        if (!"P".equals(result) && !"F".equals(result)) {
            throw new BaseException("检验结果必须为 P(合格) 或 F(不合格)");
        }

        // 更新质检单：状态、结果、明细、时间
        QmsInspection entity = new QmsInspection();
        entity.setInspectionId(id);
        entity.setStatus(InspectionStatus.ENTERED.getCode());
        entity.setResult(result);
        entity.setDetailsJson(dto.getDetailsJson() != null ? dto.getDetailsJson() : "[]");
        entity.setInspectionTime(dto.getInspectionTime());
        entity.setVersion(existing.getVersion());
        int rows = qmsInspectionMapper.updateById(entity);
        if (rows == 0) {
            throw new BaseException("数据已被其他用户修改，请刷新后重试");
        }
        log.info("录入检验结果成功：inspectionId={}, result={}, inspector={}", id, result, dto.getInspector());

        QmsInspection updated = qmsInspectionMapper.selectById(id);
        return toVO(updated);
    }

    /**
     * 统一的质检单读取权限校验。
     *
     * 权限语义（按优先级）：
     *   1. view-all → 跳过此方法，直接放行（Controller 层已校验）
     *   2. 工单执行人 → 可查看自己工单关联的所有质检单
     *   3. 当前领取人（inspectorUserId = 当前用户）→ 可查看自己领取/录入的质检单
     *   4. PENDING 且未领取 → 需 view-pending 权限才能查看（待检池）
     *   5. ENTERED 且非当前领取人 → 拒绝（不可查看他人的已录入质检单）
     *   6. CONFIRMED → 只有工单执行人和 view-all 可查看
     *
     * 列表和详情共用此方法，确保一致的权限语义。
     * 不存在的数据返回 "数据不存在"，存在但无权限返回 "无权访问"。
     */
    @Override
    public void validateInspectionOwnershipForRead(Long inspectionId) {
        QmsInspection inspection = qmsInspectionMapper.selectById(inspectionId);
        if (inspection == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        validateInspectionReadPermission(inspection);
    }

    /**
     * 对已加载的质检单实体执行读取权限校验。
     * 可供列表查询和详情查询共用。
     */
    private void validateInspectionReadPermission(QmsInspection inspection) {
        Long currentUserId = BaseContext.getCurrentId();
        String status = inspection.getStatus();

        // 1. 工单执行人可以查看自己工单关联的所有质检单
        if (inspection.getWorkOrderId() != null) {
            ProWorkOrder order = proWorkOrderMapper.selectById(inspection.getWorkOrderId());
            if (order != null && currentUserId.equals(order.getAssigneeUserId())) {
                return;
            }
        }

        // 2. 当前领取人可以查看自己领取/录入的质检单
        if (inspection.getInspectorUserId() != null
                && inspection.getInspectorUserId().equals(currentUserId)) {
            return;
        }

        // 3. PENDING 未领取 → 需 view-pending 权限
        if (InspectionStatus.PENDING.getCode().equals(status)) {
            if (!permissionService.hasPermission(currentUserId,
                    PermissionConstant.APP_INSPECTION_VIEW_PENDING)) {
                throw new com.bikeassembly.exception.ForbiddenException("您没有查看待检任务的权限");
            }
            return;
        }

        // 4. ENTERED 且非当前领取人 → 只有 view-all 可查看（Controller 已校验，这里兜底）
        if (InspectionStatus.ENTERED.getCode().equals(status)) {
            throw new com.bikeassembly.exception.ForbiddenException("您无权查看其他质检员的已录入质检单");
        }

        // 5. CONFIRMED → 只有工单执行人和 view-all 可查看
        if (InspectionStatus.CONFIRMED.getCode().equals(status)) {
            throw new com.bikeassembly.exception.ForbiddenException("您无权查看已确认的质检单");
        }

        // 6. 未知状态，保守拒绝
        throw new com.bikeassembly.exception.ForbiddenException("您无权查看此质检单");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QmsInspectionVO confirmInspection(Long id) {
        // [幂等保护]
        java.util.LinkedHashMap<String, Object> idmParams = new java.util.LinkedHashMap<>();
        idmParams.put("inspectionId", id);
        QmsInspectionVO cachedVo = checkInspectionIdempotency(
                IdempotencyService.ACTION_CONFIRM_INSPECTION, idmParams, id);
        if (cachedVo != null) {
            return cachedVo;
        }

        try {
            QmsInspectionVO result = doConfirmInspection(id);
            markInspectionIdempotencySuccess(result);
            notifyQuality(result, WsMessageDTO.EVENT_CONFIRM);
            return result;
        } catch (Exception e) {
            markInspectionIdempotencyFailure();
            throw e;
        }
    }

    /**
     * 质检确认通知：RabbitMQ 启用时发送 MQ（由消费者异步推送 WebSocket），
     * 未启用时降级为同步推送（管理端质检页）。
     */
    private void notifyQuality(QmsInspectionVO vo, String eventType) {
        com.bikeassembly.utils.AfterCommitNotification.execute(() -> sendQualityNotification(vo, eventType));
    }

    private void sendQualityNotification(QmsInspectionVO vo, String eventType) {
        MessageProducerService producer = messageProducerProvider == null
                ? null : messageProducerProvider.getIfAvailable();
        if (producer != null) {
            try {
                QualityMessageDTO message = QualityMessageDTO.builder()
                        .inspectionId(vo.getInspectionId())
                        .workOrderId(vo.getWorkOrderId())
                        .result(vo.getResult())
                        .eventType(eventType)
                        .createTime(LocalDateTime.now())
                        .build();
                producer.sendQualityMessage(message);
                return;
            } catch (Exception e) {
                log.warn("质检消息发送失败（不影响主业务）：inspectionId={}, error={}",
                        vo.getInspectionId(), e.getMessage(), e);
            }
        }
        java.util.Map<String, Object> wsData = new java.util.LinkedHashMap<>();
        wsData.put("inspectionId", vo.getInspectionId());
        wsData.put("inspectionNo", vo.getInspectionNo());
        wsData.put("workOrderId", vo.getWorkOrderId());
        wsData.put("result", vo.getResult());
        wsData.put("status", vo.getStatus());
        webSocketNotifier.pushToAdmin(WsMessageDTO.BIZ_QUALITY, eventType, wsData);
    }

    private QmsInspectionVO doConfirmInspection(Long id) {
        QmsInspection existing = qmsInspectionMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }

        // 状态机校验：只能从已录入变为已确认
        InspectionStatus currentInspectionStatus = InspectionStatus.fromCode(existing.getStatus());
        if (currentInspectionStatus == InspectionStatus.CONFIRMED) {
            throw new BaseException("该质检单已确认，请勿重复操作");
        }
        if (currentInspectionStatus == InspectionStatus.PENDING) {
            throw new BaseException("待检的质检单请先录入检验结果");
        }
        if (currentInspectionStatus != InspectionStatus.ENTERED) {
            throw new BaseException("当前状态不允许确认操作");
        }

        // 校验检验结果有效性
        String result = existing.getResult();
        if (result == null || result.isBlank() || "0".equals(result)) {
            throw new BaseException("质检结果未录入或无效，无法确认。请先录入检验结果。");
        }
        if (!"P".equals(result) && !"F".equals(result) && !"1".equals(result) && !"2".equals(result)) {
            throw new BaseException("质检结果无效：" + result + "，必须为 P/1(合格) 或 F/2(不合格)");
        }

        // 归属校验：只有当前 inspector 可以确认
        // 管理端通过 ADMIN_INSPECTION_MANAGE 或通配权限可以跨用户确认
        // 不再使用 APP_INSPECTION_VIEW_ALL 作为管理员例外（避免普通 app 用户借管理端权限语义绕过归属）
        Long currentUserId = BaseContext.getCurrentId();
        if (existing.getInspectorUserId() != null
                && !existing.getInspectorUserId().equals(currentUserId)) {
            boolean isAdminOverride = false;
            String clientType = BaseContext.getCurrentClientType();
            if ("admin".equalsIgnoreCase(clientType)) {
                isAdminOverride = permissionService.hasPermission(currentUserId,
                        PermissionConstant.ADMIN_INSPECTION_MANAGE)
                        || permissionService.hasPermission(currentUserId,
                        PermissionConstant.WILDCARD);
            }
            if (!isAdminOverride) {
                throw new BaseException("只有当前检验人可以确认质检结果");
            }
        }

        // 校验质检单关联了工单和工序执行记录
        if (existing.getWorkOrderId() == null || existing.getProcessId() == null) {
            throw new BaseException("质检单缺少工单或工序关联，无法确认");
        }
        if (existing.getProcessRecordId() == null) {
            throw new BaseException("质检单未绑定工序执行记录，无法确认");
        }

        // 并发保护：条件UPDATE — 只在 status='1' (ENTERED) 且 version 匹配时才执行
        // 使用 LambdaUpdateWrapper 替代 updateById，确保不会重复确认
        String confirmedByName = userContextHelper.getCurrentUserDisplayName();
        LambdaUpdateWrapper<QmsInspection> confirmWrapper = new LambdaUpdateWrapper<>();
        confirmWrapper.eq(QmsInspection::getInspectionId, id)
                      .eq(QmsInspection::getStatus, InspectionStatus.ENTERED.getCode())
                      .eq(QmsInspection::getVersion, existing.getVersion())
                      .set(QmsInspection::getStatus, InspectionStatus.CONFIRMED.getCode())
                      .set(QmsInspection::getConfirmedBy, confirmedByName)
                      .set(QmsInspection::getConfirmedUserId, currentUserId)
                      .set(QmsInspection::getConfirmTime, LocalDateTime.now())
                      .setSql("version = version + 1");

        int rows = qmsInspectionMapper.update(null, confirmWrapper);
        if (rows == 0) {
            // 诊断失败原因
            QmsInspection recheck = qmsInspectionMapper.selectById(id);
            if (recheck == null) {
                throw new BaseException(MessageConstant.DATA_NOT_FOUND);
            }
            if (InspectionStatus.CONFIRMED.getCode().equals(recheck.getStatus())) {
                throw new BaseException("该质检单已被确认，请勿重复操作");
            }
            throw new BaseException("数据已被其他用户修改或质检单状态已变更，请刷新后重试");
        }

        log.info("确认质检单成功：inspectionId={}, confirmedBy={}, result={}", id, confirmedByName, result);

        // ====== 联动工序状态机（同一事务中） ======
        // 注意：qcPass/qcFail 由同一事务传播，任一步失败整体回滚
        syncProcessStatusAfterConfirm(existing);

        QmsInspection updated = qmsInspectionMapper.selectById(id);
        return toVO(updated);
    }

    /**
     * 质检确认后联动更新对应工序的流转状态。
     * 找不到对应 WAIT_INSPECTION 工序时抛出异常，不允许静默成功。
     * 在同一事务中运行，任一步失败整体回滚。
     */
    private void syncProcessStatusAfterConfirm(QmsInspection inspection) {
        // 按 processRecordId 精确查找工序执行记录
        Long processRecordId = inspection.getProcessRecordId();
        if (processRecordId == null) {
            throw new BaseException("质检单未绑定工序执行记录，无法联动");
        }

        ProWorkProcess process = proWorkProcessMapper.selectById(processRecordId);
        if (process == null) {
            throw new BaseException("未找到对应工序记录：processRecordId=" + processRecordId);
        }

        // 校验工序属于同一工单
        if (!inspection.getWorkOrderId().equals(process.getWorkOrderId())) {
            throw new BaseException("质检单与工序记录工单不一致");
        }

        // 校验工序状态为待检
        if (!WorkProcessStatus.WAIT_INSPECTION.getCode().equals(process.getStatus())) {
            throw new StateMachineException(String.format(
                    "工序[%d]当前状态为[%s]，不是待检状态，无法联动质检确认",
                    processRecordId,
                    WorkProcessStatus.fromCode(process.getStatus()).getLabel()));
        }

        // 校验工序已有报工数量
        if (process.getReportQty() == null || process.getReportQty().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new BaseException("工序[%d]报工数量为0，无法确认质检。请先完成报工。");
        }

        // 根据检验结果联动工序
        String result = inspection.getResult();
        if ("P".equalsIgnoreCase(result) || "1".equals(result)) {
            proWorkProcessService.qcPass(processRecordId,
                    "质检单[" + inspection.getInspectionNo() + "]确认合格");
            log.info("质检单确认联动工序质检合格：inspectionId={}, processRecordId={}, round={}",
                    inspection.getInspectionId(), processRecordId, inspection.getInspectionRound());
        } else if ("F".equalsIgnoreCase(result) || "2".equals(result)) {
            proWorkProcessService.qcFail(processRecordId,
                    "质检单[" + inspection.getInspectionNo() + "]确认不合格");
            log.info("质检单确认联动工序质检不合格：inspectionId={}, processRecordId={}, round={}",
                    inspection.getInspectionId(), processRecordId, inspection.getInspectionRound());
        } else {
            throw new BaseException("质检结果无效：" + result + "，必须为 P(合格) 或 F(不合格)");
        }
    }

    @Override
    public QmsInspectionVO getQmsInspectionById(Long id) {
        QmsInspection entity = qmsInspectionMapper.selectById(id);
        if (entity == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        return toVO(entity);
    }

    @Override
    public PageResult<QmsInspectionVO> listQmsInspections(Long page, Long size, String inspectionNo, String itemName, String inspector, String scope, Long processRecordId) {
        // Service 层最终安全边界：重新校验 scope 合法性，防止 Controller 被绕过
        scope = ScopeValidator.requireValid(scope, ScopeValidator.MINE_PENDING_ALL);
        boolean isMine = "mine".equals(scope);
        boolean isPending = "pending".equals(scope);
        boolean hasFilter = (inspectionNo != null && !inspectionNo.isBlank())
                || (itemName != null && !itemName.isBlank())
                || (inspector != null && !inspector.isBlank())
                || processRecordId != null
                || isMine || isPending;
        Page<QmsInspection> pageParam = new Page<>(page, size);
        IPage<QmsInspection> pageResult;
        if (hasFilter) {
            Long inspectorUserId = isMine ? BaseContext.getCurrentId() : null;
            Boolean isPendingFilter = isPending ? Boolean.TRUE : null;
            pageResult = qmsInspectionMapper.selectPageByCondition(
                    pageParam, inspectionNo, itemName, inspector, inspectorUserId, isPendingFilter,
                    isMine && permissionService.hasPermission(BaseContext.getCurrentId(), PermissionConstant.APP_INSPECTION_VIEW_PENDING), processRecordId);
        } else {
            // scope=all 且无其他筛选条件：查询全部（Controller 已校验 all 权限）
            pageResult = qmsInspectionMapper.selectPage(pageParam, null);
        }
        List<QmsInspectionVO> records = pageResult.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        fillWorkOrderNumbers(records);
        return PageResult.of(records, pageResult.getTotal(), pageResult.getCurrent(), pageResult.getSize());
    }

    @Override
    public PageResult<QmsInspectionVO> listAppQmsInspections(String scope, Long workOrderId, Long processId, String status) {
        AppReadAccess access = appReadAccess(scope);
        requirePositiveFilter(workOrderId);
        requirePositiveFilter(processId);
        if (status != null && !java.util.Set.of("0", "1", "2").contains(status)) {
            throw new BaseException(400, "质检状态只能为0、1或2");
        }
        IPage<QmsInspection> page = qmsInspectionMapper.selectAppPageByCondition(new Page<>(1, 9999),
                access.userId(), access.viewAll(), access.includePending(), access.pendingOnly(),
                workOrderId, processId, status);
        List<QmsInspectionVO> records = page.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        fillWorkOrderNumbers(records);
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public long countAccessiblePending(Long workOrderId) {
        requirePositiveFilter(workOrderId);
        String scope = permissionService.hasPermission(BaseContext.getCurrentId(), PermissionConstant.APP_INSPECTION_VIEW_ALL)
                ? "all" : "mine";
        AppReadAccess access = appReadAccess(scope);
        return qmsInspectionMapper.countAppPending(workOrderId, access.userId(), access.viewAll(), access.includePending());
    }

    private record AppReadAccess(Long userId, boolean viewAll, boolean includePending, boolean pendingOnly) {}

    private AppReadAccess appReadAccess(String scope) {
        ScopeValidator.requireValid(scope, ScopeValidator.MINE_PENDING_ALL);
        Long userId = BaseContext.getCurrentId();
        if (userId == null) throw new com.bikeassembly.exception.UnauthorizedException("请先登录");
        if (!permissionService.hasPermission(userId, PermissionConstant.APP_INSPECTION_VIEW)) {
            throw new com.bikeassembly.exception.ForbiddenException("您没有查看质检任务的权限");
        }
        boolean viewAll = "all".equals(scope);
        boolean pendingOnly = "pending".equals(scope);
        boolean includePending = permissionService.hasPermission(userId, PermissionConstant.APP_INSPECTION_VIEW_PENDING);
        if ((viewAll && !permissionService.hasPermission(userId, PermissionConstant.APP_INSPECTION_VIEW_ALL))
                || (pendingOnly && !includePending)) {
            throw new com.bikeassembly.exception.ForbiddenException(MessageConstant.PERMISSION_DENIED);
        }
        return new AppReadAccess(userId, viewAll, includePending, pendingOnly);
    }

    private void requirePositiveFilter(Long id) {
        if (id != null && id <= 0) throw new BaseException(400, "工单或工序ID必须为正整数");
    }

    private void fillWorkOrderNumbers(List<QmsInspectionVO> records) {
        // 只补充当前可见质检任务的工单编号，批量查询避免 N+1；不要求生产工单查看权限。
        List<Long> workOrderIds = records.stream()
                .map(QmsInspectionVO::getWorkOrderId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (!workOrderIds.isEmpty()) {
            java.util.Map<Long, String> workOrderNumbers = proWorkOrderMapper.selectBatchIds(workOrderIds)
                    .stream()
                    .filter(order -> order.getWorkOrderNo() != null)
                    .collect(Collectors.toMap(ProWorkOrder::getWorkOrderId, ProWorkOrder::getWorkOrderNo));
            records.forEach(record -> record.setWorkOrderNo(workOrderNumbers.get(record.getWorkOrderId())));
        }
    }

    private QmsInspectionVO toVO(QmsInspection entity) {
        QmsInspectionVO vo = new QmsInspectionVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    // ==================== 幂等保护辅助方法 ====================

    private QmsInspectionVO checkInspectionIdempotency(String action,
                                                        java.util.Map<String, Object> requestParams,
                                                        Long businessId) {
        String idempotencyKey = BaseContext.getIdempotencyKey();
        if (idempotencyKey == null || BaseContext.getIdempotencyRecordId() != null) {
            return null; // 内部调用，跳过
        }

        Long userId = BaseContext.getCurrentId();
        IdempotentResult result = idempotencyService.tryAcquire(
                idempotencyKey, IdempotencyService.BIZ_TYPE_INSPECTION,
                action, requestParams, userId);

        if (result.isDuplicate()) {
            try {
                return objectMapper.readValue(result.getExistingResponse(), QmsInspectionVO.class);
            } catch (Exception e) {
                log.error("解析质检幂等缓存响应失败：recordId={}", result.getRecordId(), e);
                throw new BaseException("系统繁忙，请稍后重试");
            }
        }

        if (result.isProcessing()) {
            throw new BaseException(result.getExistingResponse());
        }

        idempotencyService.lockExecution(result.getRecordId());
        BaseContext.setIdempotencyRecordId(result.getRecordId());
        return null;
    }

    private void markInspectionIdempotencySuccess(QmsInspectionVO vo) {
        Long recordId = BaseContext.getIdempotencyRecordId();
        if (recordId == null) return;
        try {
            String responseJson = objectMapper.writeValueAsString(vo);
            idempotencyService.markSuccess(recordId, vo.getInspectionId(), responseJson);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("保存幂等响应失败：recordId={}", recordId, e);
            throw new BaseException("无法保存请求结果，请稍后重试");
        }
    }

    private void markInspectionIdempotencyFailure() {
        Long recordId = BaseContext.getIdempotencyRecordId();
        if (recordId != null) idempotencyService.markFailure(recordId, 0, "business_error");
    }
}
