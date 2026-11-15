package com.bikeassembly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.constant.PermissionConstant;
import com.bikeassembly.context.BaseContext;
import com.bikeassembly.dto.ProWorkProcessDTO;
import com.bikeassembly.entity.BaseRoute;
import com.bikeassembly.entity.ProWorkOrder;
import com.bikeassembly.entity.ProWorkProcess;
import com.bikeassembly.entity.QmsInspection;
import com.bikeassembly.entity.QmsInspectionItem;
import com.bikeassembly.enums.InspectionStatus;
import com.bikeassembly.enums.StatusTransitionValidator;
import com.bikeassembly.enums.WorkOrderStatus;
import com.bikeassembly.enums.WorkProcessStatus;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.exception.StateMachineException;
import com.bikeassembly.mapper.BaseEquipmentMapper;
import com.bikeassembly.mapper.BaseProcessMapper;
import com.bikeassembly.mapper.BaseRouteMapper;
import com.bikeassembly.mapper.ProWorkOrderMapper;
import com.bikeassembly.mapper.ProWorkProcessMapper;
import com.bikeassembly.mapper.QmsInspectionItemMapper;
import com.bikeassembly.mapper.QmsInspectionMapper;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.service.IdempotencyService;
import com.bikeassembly.service.IdempotentResult;
import com.bikeassembly.service.ProWorkProcessService;
import com.bikeassembly.service.StatusTransitionLogService;
import com.bikeassembly.utils.ScopeValidator;
import com.bikeassembly.vo.ProWorkProcessVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ProWorkProcessServiceImpl extends ServiceImpl<ProWorkProcessMapper, ProWorkProcess>
        implements ProWorkProcessService {

    @Autowired
    private ProWorkProcessMapper proWorkProcessMapper;
    @Autowired
    private ProWorkOrderMapper proWorkOrderMapper;
    @Autowired
    private BaseProcessMapper baseProcessMapper;
    @Autowired
    private BaseEquipmentMapper baseEquipmentMapper;
    @Autowired
    private BaseRouteMapper baseRouteMapper;
    @Autowired
    private StatusTransitionLogService statusTransitionLogService;
    @Autowired
    private ProWorkOrderServiceImpl proWorkOrderServiceImpl;
    @Autowired
    private QmsInspectionMapper qmsInspectionMapper;
    @Autowired
    private QmsInspectionItemMapper qmsInspectionItemMapper;
    @Autowired
    private IdempotencyService idempotencyService;
    @Autowired
    private com.bikeassembly.service.PermissionService permissionService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ObjectMapper objectMapper;

    // ==================== 基础 CRUD ====================

    @Override
    public ProWorkProcessVO saveProWorkProcess(ProWorkProcessDTO dto) {
        checkBusinessRules(dto);

        if (dto.getWorkOrderId() == null || proWorkOrderMapper.selectById(dto.getWorkOrderId()) == null) {
            throw new BaseException("工单不存在");
        }
        if (dto.getProcessId() == null || baseProcessMapper.selectById(dto.getProcessId()) == null) {
            throw new BaseException("工序不存在");
        }

        ProWorkProcess entity = toEntity(dto);
        // 新增工序记录时强制由后端控制字段，不允许前端任意传入：
        // - 状态强制为 PENDING（待开始），由工艺路线下达或手动指派时再设置
        entity.setStatus(WorkProcessStatus.PENDING.getCode());
        // - 报工数量初始为 0（实际数量在报工时原子累加）
        entity.setReportQty(BigDecimal.ZERO);
        // - 开始/结束时间在实际操作时由状态机端点写入（start/complete/report）
        entity.setStartTime(null);
        entity.setEndTime(null);
        // - 质检结果初始为空
        entity.setQcResult(null);
        // - 操作人字段在实际开工时由状态机端点写入
        entity.setOperator(null);
        entity.setOperatorUserId(null);
        // 尝试从工艺路线解析工序顺序号
        if (entity.getStepSeq() == null || entity.getStepSeq() == 0) {
            entity.setStepSeq(resolveStepSeq(dto.getWorkOrderId(), dto.getProcessId()));
        }
        entity.setVersion(0);
        entity.setReworkCount(0);
        proWorkProcessMapper.insert(entity);
        log.info("新增生产工序记录成功：processRecordId={}", entity.getProcessRecordId());

        return toVO(entity);
    }

    @Override
    public ProWorkProcessVO updateProWorkProcess(Long id, ProWorkProcessDTO dto) {
        ProWorkProcess existing = proWorkProcessMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        // 终端状态不允许修改
        WorkProcessStatus currentStatus = WorkProcessStatus.fromCode(existing.getStatus());
        if (currentStatus.isTerminal()) {
            throw new BaseException("已" + currentStatus.getLabel() + "的工序记录不可修改");
        }

        // 生产开始后（非PENDING/READY）禁止通过普通CRUD修改
        if (currentStatus != WorkProcessStatus.PENDING && currentStatus != WorkProcessStatus.READY) {
            throw new BaseException("已" + currentStatus.getLabel() + "的工序不可通过普通编辑修改，请使用专用状态操作");
        }

        // 拒绝客户端提交受业务状态机保护的字段
        rejectProtectedField(dto.getWorkOrderId(), existing.getWorkOrderId(), "workOrderId");
        rejectProtectedField(dto.getProcessId(), existing.getProcessId(), "processId");
        rejectProtectedField(dto.getStepSeq(), existing.getStepSeq(), "stepSeq");
        rejectProtectedField(dto.getStatus(), existing.getStatus(), "status");
        rejectProtectedField(dto.getReportQty(), existing.getReportQty(), "reportQty");
        rejectProtectedField(dto.getStartTime(), existing.getStartTime(), "startTime");
        rejectProtectedField(dto.getEndTime(), existing.getEndTime(), "endTime");
        rejectProtectedField(dto.getReworkCount(), existing.getReworkCount(), "reworkCount");
        rejectProtectedField(dto.getQcResult(), existing.getQcResult(), "qcResult");
        rejectProtectedField(dto.getOperatorUserId(), existing.getOperatorUserId(), "operatorUserId");
        rejectProtectedField(dto.getOperator(), existing.getOperator(), "operator");
        rejectProtectedField(dto.getVersion(), existing.getVersion(), "version");
        rejectProtectedField(dto.getCreateTime(), existing.getCreateTime(), "createTime");

        checkBusinessRules(dto);

        ProWorkProcess entity = toEntity(dto);
        entity.setProcessRecordId(id);
        entity.setVersion(existing.getVersion());
        // 覆盖所有受保护字段为原始值
        entity.setWorkOrderId(existing.getWorkOrderId());
        entity.setProcessId(existing.getProcessId());
        entity.setStepSeq(existing.getStepSeq());
        entity.setStatus(existing.getStatus());
        entity.setStartTime(existing.getStartTime());
        entity.setEndTime(existing.getEndTime());
        entity.setReportQty(existing.getReportQty());
        entity.setOperator(existing.getOperator());
        entity.setOperatorUserId(existing.getOperatorUserId());
        entity.setQcResult(existing.getQcResult());
        entity.setReworkCount(existing.getReworkCount());
        int rows = proWorkProcessMapper.updateById(entity);
        if (rows == 0) {
            throw new BaseException(MessageConstant.DATA_CONCURRENT_MODIFIED);
        }
        log.info("修改生产工序记录成功：processRecordId={}", id);

        ProWorkProcess updated = proWorkProcessMapper.selectById(id);
        return toVO(updated);
    }

    /** 如果DTO提交了与现有值不同的受保护字段，直接抛出业务异常 */
    private void rejectProtectedField(Object dtoValue, Object existingValue, String fieldName) {
        if (dtoValue != null && !dtoValue.equals(existingValue)) {
            throw new BaseException("字段[" + fieldName + "]不允许通过普通编辑修改");
        }
    }

    @Override
    public ProWorkProcessVO getProWorkProcessById(Long id) {
        // 使用带 JOIN sys_user 的查询，确保 operator 显示名正确
        ProWorkProcess entity = proWorkProcessMapper.selectByIdWithOperator(id);
        if (entity == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        return toVO(entity);
    }

    /**
     * [答辩入口] 工序记录分页查询，支持 scope=mine|all。
     * scope=mine：返回当前用户操作的工序 OR 工单指派给当前用户的工序（JOIN pro_work_order）
     * scope=all：返回全部（需 APP_WORK_PROCESS_VIEW_ALL 权限）
     *
     * Service 层作为最终安全边界：再次校验 scope 合法性 + all 权限，即使被其他代码直接调用也不会绕过。
     */
    @Override
    public PageResult<ProWorkProcessVO> listProWorkProcesses(Long page, Long size, String scope) {
        // Service 层最终安全边界：重新校验 scope 合法性，防止 Controller 被绕过
        scope = ScopeValidator.requireValid(scope, ScopeValidator.MINE_OR_ALL);
        Page<ProWorkProcess> pageParam = new Page<>(page, size);
        IPage<ProWorkProcess> pageResult;

        // mine 永远表示"当前用户的工序"，即使 data_scope=ALL 也不自动变 all
        // 统一使用 selectPageByCondition（始终 JOIN sys_user 获取操作员姓名）
        if ("mine".equals(scope)) {
            Long currentUserId = BaseContext.getCurrentId();
            pageResult = proWorkProcessMapper.selectPageByCondition(pageParam, null, currentUserId);
        } else if ("all".equals(scope)) {
            // scope=all 时不过滤指派执行人，但仍 JOIN sys_user 获取操作员显示名
            pageResult = proWorkProcessMapper.selectPageByCondition(pageParam, null, null);
        } else {
            // requireValid 已保证不会走到这里；保留此分支作防御性编程
            throw new com.bikeassembly.exception.BaseException(MessageConstant.SCOPE_INVALID);
        }

        List<ProWorkProcessVO> records = pageResult.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResult.of(records, pageResult.getTotal(), pageResult.getCurrent(), pageResult.getSize());
    }

    @Override
    public PageResult<ProWorkProcessVO> listAppProWorkProcesses(String scope, Long workOrderId) {
        ScopeValidator.requireValid(scope, ScopeValidator.MINE_OR_ALL);
        Long userId = BaseContext.getCurrentId();
        if (userId == null) throw new com.bikeassembly.exception.UnauthorizedException("请先登录");
        if (!permissionService.hasPermission(userId, PermissionConstant.APP_WORK_PROCESS_VIEW)
                || ("all".equals(scope) && !permissionService.hasPermission(userId, PermissionConstant.APP_WORK_PROCESS_VIEW_ALL))) {
            throw new com.bikeassembly.exception.ForbiddenException(MessageConstant.PERMISSION_DENIED);
        }
        if (workOrderId != null && workOrderId <= 0) throw new BaseException(400, "工单ID必须为正整数");
        IPage<ProWorkProcess> page = proWorkProcessMapper.selectAppPageByCondition(new Page<>(1, 9999),
                "mine".equals(scope) ? userId : null, workOrderId);
        return PageResult.of(page.getRecords().stream().map(this::toVO).collect(Collectors.toList()),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public void deleteProWorkProcess(Long id) {
        // 生产执行工序由工单下达自动生成，不允许物理删除。
        // 需要移除工序时，使用 SKIPPED 或 CANCELLED 专用状态动作。
        throw new BaseException("生产工序不支持物理删除，请使用跳过或取消操作");
    }

    // ==================== 状态机：开始工序 ====================

    /**
     * [答辩入口] 工序开始 —— 工序流转状态机的核心操作之一。
     * [状态变化] READY → IN_PROGRESS
     * [业务规则] 开始条件（按判断顺序）：
     *   1. 工单必须处于可生产状态（RELEASED 或 IN_PROGRESS），暂停中不可操作
     *   2. 工序状态必须是 READY（前序工序完成后自动标记的）
     *   3. 前序工序必须已完成（COMPLETED）或被跳过（SKIPPED）
     * [调用链] Controller → ProWorkProcessService.startProWorkProcess → optimisticUpdate → ProWorkProcessMapper.updateById
     * [事务] @Transactional(rollbackFor = Exception.class)
     * [并发控制] 乐观锁：乐观更新 version，更新行数为 0 时抛 StateMachineException
     * [状态变化联动] 任何可执行工序开工时，工单若仍为 RELEASED，自动转为 IN_PROGRESS（不再依赖 stepSeq==1）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProWorkProcessVO startProWorkProcess(Long id) {
        // [幂等保护] 在所有业务逻辑之前检查幂等
        java.util.LinkedHashMap<String, Object> idmParams = new java.util.LinkedHashMap<>();
        idmParams.put("processRecordId", id);
        ProWorkProcessVO cachedVo = checkIdempotencyAndProceed(
                IdempotencyService.ACTION_START, idmParams, id);
        if (cachedVo != null) {
            return cachedVo;
        }

        try {
            ProWorkProcessVO result = doStartProWorkProcess(id);
            markIdempotencySuccess(result);
            return result;
        } catch (Exception e) {
            markIdempotencyFailure();
            throw e;
        }
    }

    private ProWorkProcessVO doStartProWorkProcess(Long id) {
        // 1. 查询当前工序记录，获取当前状态
        ProWorkProcess existing = getByIdOrThrow(id);
        WorkProcessStatus currentStatus = WorkProcessStatus.fromCode(existing.getStatus());

        // 2. 校验当前用户是工单执行人
        validateProcessOwnership(existing);

        // 3. 校验工单必须处于可生产状态（RELEASED 或 IN_PROGRESS），暂停中不可操作
        validateWorkOrderActive(existing.getWorkOrderId());

        // 4. 校验工序状态：只能从 READY 开始
        if (currentStatus != WorkProcessStatus.READY) {
            throw new StateMachineException(
                    String.format("工序[%d]当前状态为[%s]，只有可开工的工序才能开始", id, currentStatus.getLabel()));
        }

        // 5. 校验前序工序：前序必须已完成（COMPLETED）或被跳过（SKIPPED）
        validatePredecessorCompletion(existing.getWorkOrderId(), existing.getStepSeq());

        // 6. 状态转换校验：READY → IN_PROGRESS
        requireProcessTransition(currentStatus, WorkProcessStatus.IN_PROGRESS, id);

        // 7. 乐观锁更新：状态改为 IN_PROGRESS，记录开始时间和操作人
        optimisticUpdate(id, existing.getVersion(),
                WorkProcessStatus.IN_PROGRESS.getCode(), LocalDateTime.now(), BaseContext.getCurrentId());

        // 8. 记录状态转换日志
        statusTransitionLogService.logTransition("WORK_PROCESS", id,
                currentStatus.getCode(), WorkProcessStatus.IN_PROGRESS.getCode(), "start", null);

        // 9. 联动：任何可执行工序第一次开工时，工单若仍为 RELEASED，自动转为 IN_PROGRESS
        // （onFirstProcessStarted 内部有状态守卫，即使多次调用也安全幂等）
        proWorkOrderServiceImpl.onFirstProcessStarted(existing.getWorkOrderId());

        log.info("开始工序成功：processRecordId={}, userId={}", id, BaseContext.getCurrentId());
        return getByIdAsVO(id);
    }

    // ==================== 状态机：报工并送检 ====================

    /**
     * [答辩入口] 报工并送检（需要质检的工序）。
     * [状态变化] IN_PROGRESS → WAIT_INSPECTION（待检）
     * [核心业务] 工序完成后不直接标记完成，而是进入待检状态，等待质检员判定
     * [业务规则] qcResult 设置为 "0"（待检）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProWorkProcessVO reportWithInspection(Long id, BigDecimal reportQty, String qcResultNote) {
        // 1. 查询当前工序记录，获取当前状态
        ProWorkProcess existing = getByIdOrThrow(id);
        WorkProcessStatus currentStatus = WorkProcessStatus.fromCode(existing.getStatus());

        // 2. 校验当前用户是工单执行人
        validateProcessOwnership(existing);

        // 3. 校验工序状态：只有进行中（IN_PROGRESS）的工序才能报工送检
        if (currentStatus != WorkProcessStatus.IN_PROGRESS) {
            throw new StateMachineException(
                    String.format("工序[%d]当前状态为[%s]，只有进行中的工序才能报工送检", id, currentStatus.getLabel()));
        }

        // 4. 校验工单必须处于可生产状态
        validateWorkOrderActive(existing.getWorkOrderId());

        // 5. 状态转换校验：IN_PROGRESS → WAIT_INSPECTION（待检）
        requireProcessTransition(currentStatus, WorkProcessStatus.WAIT_INSPECTION, id);

        // 6. 乐观锁更新：原子累加报工数量，状态改为 WAIT_INSPECTION，qcResult 设为 "0"（待检）
        optimisticUpdateWithReport(id, existing.getVersion(),
                WorkProcessStatus.WAIT_INSPECTION.getCode(), reportQty, "0", qcResultNote);

        // 7. 自动生成质检任务（同一事务）
        autoCreateInspection(existing.getWorkOrderId(), existing.getProcessId(), id);

        // 8. 记录状态转换日志
        statusTransitionLogService.logTransition("WORK_PROCESS", id,
                currentStatus.getCode(), WorkProcessStatus.WAIT_INSPECTION.getCode(),
                "report_with_qc", qcResultNote);

        log.info("报工送检成功：processRecordId={}, reportQty={}", id, reportQty);
        return getByIdAsVO(id);
    }

    // ==================== 状态机：统一报工 ====================

    /**
     * 统一报工入口：后端根据当前工序是否存在启用的质检项，自动决定目标状态。
     * - 无需质检：IN_PROGRESS → COMPLETED，推进下一道
     * - 需质检：IN_PROGRESS → WAIT_INSPECTION，自动创建质检任务
     * - 部分报工（reportQty 累计 < plan_qty）：保持 IN_PROGRESS，不推进
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProWorkProcessVO unifiedReport(Long id, BigDecimal reportQty, String qcResultNote) {
        // [幂等保护] 0. 在所有业务逻辑之前检查幂等（同一Key+相同参数只执行一次）
        java.util.LinkedHashMap<String, Object> idmParams = new java.util.LinkedHashMap<>();
        idmParams.put("processRecordId", id);
        idmParams.put("reportQty", reportQty);
        idmParams.put("qcResultNote", qcResultNote);
        ProWorkProcessVO cachedVo = checkIdempotencyAndProceed(
                IdempotencyService.ACTION_UNIFIED_REPORT, idmParams, id);
        if (cachedVo != null) {
            return cachedVo;
        }

        try {
            ProWorkProcessVO result = doUnifiedReport(id, reportQty, qcResultNote);
            markIdempotencySuccess(result);
            return result;
        } catch (Exception e) {
            markIdempotencyFailure();
            throw e;
        }
    }

    /**
     * 统一报工核心逻辑（不含幂等检查，幂等检查在 unifiedReport 中完成）。
     */
    private ProWorkProcessVO doUnifiedReport(Long id, BigDecimal reportQty, String qcResultNote) {
        // 1. 校验报工数量
        if (reportQty == null || reportQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BaseException("报工数量必须大于0");
        }

        // 2. 查询当前工序
        ProWorkProcess existing = getByIdOrThrow(id);
        WorkProcessStatus currentStatus = WorkProcessStatus.fromCode(existing.getStatus());

        // 3. 校验归属和工单状态
        validateProcessOwnership(existing);
        validateWorkOrderActive(existing.getWorkOrderId());

        // 4. 只允许 IN_PROGRESS 状态报工
        if (currentStatus != WorkProcessStatus.IN_PROGRESS) {
            throw new StateMachineException(
                    String.format("工序[%d]当前状态为[%s]，只有进行中的工序才能报工", id, currentStatus.getLabel()));
        }

        // 5. 原子累加报工数量
        int rows = proWorkProcessMapper.accumulateReportQty(id, existing.getVersion(), reportQty);
        if (rows == 0) {
            ProWorkProcess current = proWorkProcessMapper.selectById(id);
            if (current == null) {
                throw new StateMachineException(MessageConstant.DATA_NOT_FOUND);
            }
            if (!existing.getVersion().equals(current.getVersion())) {
                throw new StateMachineException(MessageConstant.DATA_CONCURRENT_MODIFIED);
            }
            throw new StateMachineException("报工数量超出计划数量或工序状态已变更，请刷新后重试");
        }

        // 6. 重新读取当前工序（version 已被 accumulateReportQty +1）
        ProWorkProcess refreshed = proWorkProcessMapper.selectById(id);
        BigDecimal cumulativeQty = refreshed.getReportQty();
        ProWorkOrder order = proWorkOrderMapper.selectById(refreshed.getWorkOrderId());
        BigDecimal planQty = order != null ? order.getPlanQty() : BigDecimal.ZERO;

        // 7. 判断是否报满
        if (cumulativeQty.compareTo(planQty) < 0) {
            // 部分报工：保持 IN_PROGRESS，不设置 endTime，不解锁下一道
            // accumulateReportQty 已原子累加数量并自增 version，无需额外更新
            log.info("部分报工成功：processRecordId={}, cumulativeQty={}, planQty={}", id, cumulativeQty, planQty);
            return getByIdAsVO(id);
        }

        // 8. 报满：判断是否需要质检
        boolean needsQc = hasInspectionItems(refreshed.getProcessId());

        if (!needsQc) {
            // 无需质检：IN_PROGRESS → COMPLETED
            requireProcessTransition(currentStatus, WorkProcessStatus.COMPLETED, id);
            ProWorkProcess update = new ProWorkProcess();
            update.setProcessRecordId(id);
            update.setStatus(WorkProcessStatus.COMPLETED.getCode());
            update.setEndTime(LocalDateTime.now());
            update.setVersion(refreshed.getVersion());
            int statusRows = proWorkProcessMapper.updateById(update);
            if (statusRows == 0) {
                throw new StateMachineException(MessageConstant.DATA_CONCURRENT_MODIFIED);
            }
            statusTransitionLogService.logTransition("WORK_PROCESS", id,
                    currentStatus.getCode(), WorkProcessStatus.COMPLETED.getCode(),
                    "unified_report_complete", qcResultNote);
            // 推进下一道
            advanceToNextProcess(refreshed.getWorkOrderId(), refreshed.getStepSeq());
            log.info("统一报工完成（无需质检）：processRecordId={}, reportQty={}", id, reportQty);
        } else {
            // 需要质检：IN_PROGRESS → WAIT_INSPECTION
            requireProcessTransition(currentStatus, WorkProcessStatus.WAIT_INSPECTION, id);
            ProWorkProcess update = new ProWorkProcess();
            update.setProcessRecordId(id);
            update.setStatus(WorkProcessStatus.WAIT_INSPECTION.getCode());
            update.setQcResult("0"); // 待检
            update.setEndTime(LocalDateTime.now());
            if (qcResultNote != null) {
                update.setQcResultNote(qcResultNote);
            }
            update.setVersion(refreshed.getVersion());
            int statusRows = proWorkProcessMapper.updateById(update);
            if (statusRows == 0) {
                throw new StateMachineException(MessageConstant.DATA_CONCURRENT_MODIFIED);
            }
            // 自动创建质检任务
            autoCreateInspection(refreshed.getWorkOrderId(), refreshed.getProcessId(), id);
            statusTransitionLogService.logTransition("WORK_PROCESS", id,
                    currentStatus.getCode(), WorkProcessStatus.WAIT_INSPECTION.getCode(),
                    "unified_report_qc", qcResultNote);
            log.info("统一报工送检：processRecordId={}, reportQty={}", id, reportQty);
        }

        return getByIdAsVO(id);
    }

    // ==================== 状态机：质检 ====================

    /**
     * [内部方法] 质检通过。仅供 QmsInspectionServiceImpl.confirmInspection() 调用。
     * 禁止 Controller 或其他外部代码直接调用。所有质检判定必须通过质检单确认流程。
     * [状态变化] WAIT_INSPECTION → QUALIFIED → COMPLETED（两步自动完成）
     * [核心业务] 质检通过后自动标记为完成，并推进下一道工序
     * [业务规则] qcResult 设置为 "1"（合格）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @Deprecated
    public ProWorkProcessVO qcPass(Long id, String qcNote) {
        return doQcPass(id, qcNote);
    }

    private ProWorkProcessVO doQcPass(Long id, String qcNote) {
        // 1. 查询当前工序记录，获取当前状态
        ProWorkProcess existing = getByIdOrThrow(id);
        WorkProcessStatus currentStatus = WorkProcessStatus.fromCode(existing.getStatus());

        // 2. 校验工序状态：只有待检（WAIT_INSPECTION）的工序才能质检
        if (currentStatus != WorkProcessStatus.WAIT_INSPECTION) {
            throw new StateMachineException(
                    String.format("工序[%d]当前状态为[%s]，只有待检的工序才能质检", id, currentStatus.getLabel()));
        }

        // 3. 状态转换校验：WAIT_INSPECTION → QUALIFIED（检验合格）
        requireProcessTransition(currentStatus, WorkProcessStatus.QUALIFIED, id);

        // 4. 乐观锁更新：状态改为 QUALIFIED，qcResult 设为 "1"（合格）
        optimisticUpdateWithQc(id, existing.getVersion(),
                WorkProcessStatus.QUALIFIED.getCode(), "1", qcNote);

        // 5. 记录质检合格日志
        statusTransitionLogService.logTransition("WORK_PROCESS", id,
                currentStatus.getCode(), WorkProcessStatus.QUALIFIED.getCode(), "qc_pass", qcNote);

        // 6. 自动流转：QUALIFIED → COMPLETED（质检合格后自动完成）
        ProWorkProcess qualified = proWorkProcessMapper.selectById(id);
        optimisticUpdate(id, qualified.getVersion(),
                WorkProcessStatus.COMPLETED.getCode(), null, null);

        statusTransitionLogService.logTransition("WORK_PROCESS", id,
                WorkProcessStatus.QUALIFIED.getCode(), WorkProcessStatus.COMPLETED.getCode(),
                "qualified_complete", null);

        // 7. 联动：自动推进下一道工序
        advanceToNextProcess(existing.getWorkOrderId(), existing.getStepSeq());

        log.info("质检通过并完成：processRecordId={}", id);
        return getByIdAsVO(id);
    }

    /**
     * [内部方法] 质检不合格。仅供 QmsInspectionServiceImpl.confirmInspection() 调用。
     * 禁止 Controller 或其他外部代码直接调用。所有质检判定必须通过质检单确认流程。
     * [状态变化] WAIT_INSPECTION（待检）→ REJECTED（检验不合格）
     * [业务规则] 只有待检状态的工序才能质检，不合格后 qcResult 设置为 "2"（不合格）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @Deprecated
    public ProWorkProcessVO qcFail(Long id, String qcNote) {
        return doQcFail(id, qcNote);
    }

    private ProWorkProcessVO doQcFail(Long id, String qcNote) {
        // 1. 查询当前工序记录，获取当前状态
        ProWorkProcess existing = getByIdOrThrow(id);
        WorkProcessStatus currentStatus = WorkProcessStatus.fromCode(existing.getStatus());

        // 2. 校验工序状态：只有待检（WAIT_INSPECTION）的工序才能进行质检判定
        if (currentStatus != WorkProcessStatus.WAIT_INSPECTION) {
            throw new StateMachineException(
                    String.format("工序[%d]当前状态为[%s]，只有待检的工序才能质检", id, currentStatus.getLabel()));
        }

        // 3. 状态转换校验：WAIT_INSPECTION → REJECTED（检验不合格）
        requireProcessTransition(currentStatus, WorkProcessStatus.REJECTED, id);

        // 4. 乐观锁更新：状态改为 REJECTED，qcResult 设为 "2"（不合格）
        optimisticUpdateWithQc(id, existing.getVersion(),
                WorkProcessStatus.REJECTED.getCode(), "2", qcNote);

        // 5. 记录质检不合格日志
        statusTransitionLogService.logTransition("WORK_PROCESS", id,
                currentStatus.getCode(), WorkProcessStatus.REJECTED.getCode(), "qc_fail", qcNote);

        log.info("质检不合格：processRecordId={}", id);
        return getByIdAsVO(id);
    }

    // ==================== 状态机：返工 ====================

    /**
     * [答辩入口] 操作员返工开始。
     * [状态变化] REJECTED → REWORK
     * [权限] 要求当前用户是工单执行人。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProWorkProcessVO operatorReworkStart(Long id) {
        return reworkStartWithOwnershipCheck(id, true);
    }

    @Override
    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public ProWorkProcessVO reworkStart(Long id) {
        return operatorReworkStart(id);
    }

    /**
     * [答辩入口] 管理端强制返工开始。
     * [状态变化] REJECTED → REWORK
     * [权限] 要求管理员拥有 admin:work-process:rework 权限，不校验执行人归属。
     *        记录操作者、客户端和返工原因。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProWorkProcessVO adminReworkStart(Long id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BaseException("管理端强制返工必须填写原因");
        }
        // 管理端权限二次确认（Service 是最终安全边界）
        if (!permissionService.hasPermission(BaseContext.getCurrentId(),
                com.bikeassembly.constant.PermissionConstant.ADMIN_WORK_PROCESS_REWORK)) {
            throw new com.bikeassembly.exception.ForbiddenException(
                    "管理端强制返工需要 admin:work-process:rework 权限");
        }
        return reworkStartWithOwnershipCheck(id, false);
    }

    /**
     * 返工开始共享核心逻辑。
     * @param checkOwnership true=操作员模式（校验工单执行人归属），false=管理员模式（跳过归属校验）
     */
    private ProWorkProcessVO reworkStartWithOwnershipCheck(Long id, boolean checkOwnership) {
        // [幂等保护]
        java.util.LinkedHashMap<String, Object> idmParams = new java.util.LinkedHashMap<>();
        idmParams.put("processRecordId", id);
        ProWorkProcessVO cachedVo = checkIdempotencyAndProceed(
                IdempotencyService.ACTION_REWORK_START, idmParams, id);
        if (cachedVo != null) {
            return cachedVo;
        }

        try {
            ProWorkProcessVO result = doReworkStartCore(id, checkOwnership);
            markIdempotencySuccess(result);
            return result;
        } catch (Exception e) {
            markIdempotencyFailure();
            throw e;
        }
    }

    private ProWorkProcessVO doReworkStartCore(Long id, boolean checkOwnership) {
        // 1. 查询当前工序记录
        ProWorkProcess existing = getByIdOrThrow(id);
        WorkProcessStatus currentStatus = WorkProcessStatus.fromCode(existing.getStatus());

        // 2. 归属校验：仅操作员模式校验工单执行人
        if (checkOwnership) {
            validateProcessOwnership(existing);
        }

        // 3. 校验工序状态：只有检验不合格（REJECTED）的工序才能发起返工
        if (currentStatus != WorkProcessStatus.REJECTED) {
            throw new StateMachineException(
                    String.format("工序[%d]当前状态为[%s]，只有检验不合格的工序才能发起返工", id, currentStatus.getLabel()));
        }

        // 4. 状态转换校验：REJECTED → REWORK
        requireProcessTransition(currentStatus, WorkProcessStatus.REWORK, id);

        // 5. 返工次数 +1
        int newReworkCount = (existing.getReworkCount() != null ? existing.getReworkCount() : 0) + 1;

        // 6. 乐观锁更新：状态改为 REWORK，记录开始时间
        ProWorkProcess update = new ProWorkProcess();
        update.setStatus(WorkProcessStatus.REWORK.getCode());
        update.setReworkCount(newReworkCount);
        update.setStartTime(LocalDateTime.now());

        optimisticUpdateEntity(id, existing.getVersion(), update);

        // 7. 记录返工开始日志（区分操作员返工和管理员强制返工）
        String logAction = checkOwnership ? "operator_rework" : "admin_force_rework";
        String logRemark = checkOwnership
                ? "第" + newReworkCount + "次返工"
                : "管理员[" + BaseContext.getCurrentId() + "]强制第" + newReworkCount + "次返工，" +
                  "客户端=" + BaseContext.getCurrentClientType();
        statusTransitionLogService.logTransition("WORK_PROCESS", id,
                currentStatus.getCode(), WorkProcessStatus.REWORK.getCode(), logAction, logRemark);

        log.info("{}返工：processRecordId={}, reworkCount={}", checkOwnership ? "操作员" : "管理员强制", id, newReworkCount);
        return getByIdAsVO(id);
    }

    // ==================== 返工完成 ====================

    /**
     * [答辩入口] 操作员返工完成。
     * [状态变化] REWORK → WAIT_INSPECTION
     * [权限] 要求当前用户是工单执行人。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProWorkProcessVO operatorReworkComplete(Long id) {
        return reworkCompleteWithOwnershipCheck(id, true, null);
    }

    @Override
    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public ProWorkProcessVO reworkComplete(Long id) {
        return operatorReworkComplete(id);
    }

    /**
     * [答辩入口] 管理端强制返工完成。
     * [状态变化] REWORK → WAIT_INSPECTION
     * [权限] 要求管理员拥有 admin:work-process:rework 权限，不校验执行人归属。
     *        remark 为可选备注（非报工数量）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProWorkProcessVO adminReworkComplete(Long id, String remark) {
        if (!permissionService.hasPermission(BaseContext.getCurrentId(),
                com.bikeassembly.constant.PermissionConstant.ADMIN_WORK_PROCESS_REWORK)) {
            throw new com.bikeassembly.exception.ForbiddenException(
                    "管理端强制返工完成需要 admin:work-process:rework 权限");
        }
        return reworkCompleteWithOwnershipCheck(id, false, remark);
    }

    /**
     * 返工完成共享核心逻辑。
     * @param checkOwnership true=操作员模式，false=管理员模式
     * @param adminRemark 管理员备注（仅管理员模式使用）
     */
    private ProWorkProcessVO reworkCompleteWithOwnershipCheck(Long id, boolean checkOwnership, String adminRemark) {
        // [幂等保护]
        java.util.LinkedHashMap<String, Object> idmParams = new java.util.LinkedHashMap<>();
        idmParams.put("processRecordId", id);
        ProWorkProcessVO cachedVo = checkIdempotencyAndProceed(
                IdempotencyService.ACTION_REWORK_COMPLETE, idmParams, id);
        if (cachedVo != null) {
            return cachedVo;
        }

        try {
            ProWorkProcessVO result = doReworkCompleteCore(id, checkOwnership, adminRemark);
            markIdempotencySuccess(result);
            return result;
        } catch (Exception e) {
            markIdempotencyFailure();
            throw e;
        }
    }

    private ProWorkProcessVO doReworkCompleteCore(Long id, boolean checkOwnership, String adminRemark) {
        // 1. 查询当前工序记录
        ProWorkProcess existing = getByIdOrThrow(id);
        WorkProcessStatus currentStatus = WorkProcessStatus.fromCode(existing.getStatus());

        // 2. 归属校验：仅操作员模式校验工单执行人
        if (checkOwnership) {
            validateProcessOwnership(existing);
        }

        // 3. 校验工序状态：只有返工中（REWORK）的工序才能完成返工
        if (currentStatus != WorkProcessStatus.REWORK) {
            throw new StateMachineException(
                    String.format("工序[%d]当前状态为[%s]，只有返工中的工序才能完成返工", id, currentStatus.getLabel()));
        }

        // 4. 状态转换校验：REWORK → WAIT_INSPECTION
        requireProcessTransition(currentStatus, WorkProcessStatus.WAIT_INSPECTION, id);

        // 5. 乐观锁更新：状态→WAIT_INSPECTION，qcResult→3(待复检)，不累加报工数量
        ProWorkProcess update = new ProWorkProcess();
        update.setStatus(WorkProcessStatus.WAIT_INSPECTION.getCode());
        update.setQcResult("3"); // 待复检
        update.setEndTime(LocalDateTime.now());

        optimisticUpdateEntity(id, existing.getVersion(), update);

        // 6. 自动生成新的复检任务
        autoCreateInspection(existing.getWorkOrderId(), existing.getProcessId(), id);

        // 7. 记录返工完成日志（区分操作员返工和管理员强制返工）
        String logAction = checkOwnership ? "operator_rework" : "admin_force_rework";
        String logRemark = checkOwnership
                ? "返工完成，待复检"
                : "管理员[" + BaseContext.getCurrentId() + "]强制返工完成，待复检"
                  + (adminRemark != null && !adminRemark.isBlank() ? "。备注：" + adminRemark : "");
        statusTransitionLogService.logTransition("WORK_PROCESS", id,
                currentStatus.getCode(), WorkProcessStatus.WAIT_INSPECTION.getCode(),
                logAction, logRemark);

        log.info("{}返工完成，待复检：processRecordId={}", checkOwnership ? "操作员" : "管理员强制", id);
        return getByIdAsVO(id);
    }

    // ==================== 状态机：跳过/取消 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProWorkProcessVO skipProcess(Long id, String remark) {
        // [幂等保护]
        java.util.LinkedHashMap<String, Object> idmParams = new java.util.LinkedHashMap<>();
        idmParams.put("processRecordId", id);
        idmParams.put("remark", remark);
        ProWorkProcessVO cachedVo = checkIdempotencyAndProceed(
                IdempotencyService.ACTION_SKIP, idmParams, id);
        if (cachedVo != null) {
            return cachedVo;
        }

        try {
            // 1. 查询当前工序记录，获取当前状态
            ProWorkProcess existing = getByIdOrThrow(id);
            WorkProcessStatus currentStatus = WorkProcessStatus.fromCode(existing.getStatus());

            // 2. 状态转换校验：允许从 PENDING（待开始）或 READY（可开工）→ SKIPPED（已跳过）
            requireProcessTransition(currentStatus, WorkProcessStatus.SKIPPED, id);

            // 3. 乐观锁更新：状态改为 SKIPPED
            optimisticUpdateStatusOnly(id, existing.getVersion(), WorkProcessStatus.SKIPPED.getCode());

            // 4. 记录跳过日志
            statusTransitionLogService.logTransition("WORK_PROCESS", id,
                    currentStatus.getCode(), WorkProcessStatus.SKIPPED.getCode(), "skip", remark);

            // 5. 联动：被跳过的工序也要触发推进下一道（不阻塞工序流转）
            advanceToNextProcess(existing.getWorkOrderId(), existing.getStepSeq());

            log.info("跳过工序：processRecordId={}, remark={}", id, remark);
            ProWorkProcessVO vo = getByIdAsVO(id);
            markIdempotencySuccess(vo);
            return vo;
        } catch (Exception e) {
            markIdempotencyFailure();
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProWorkProcessVO cancelProcess(Long id, String remark) {
        // [幂等保护]
        java.util.LinkedHashMap<String, Object> idmParams = new java.util.LinkedHashMap<>();
        idmParams.put("processRecordId", id);
        idmParams.put("remark", remark);
        ProWorkProcessVO cachedVo = checkIdempotencyAndProceed(
                IdempotencyService.ACTION_CANCEL, idmParams, id);
        if (cachedVo != null) {
            return cachedVo;
        }

        try {
            // 1. 查询当前工序记录，获取当前状态
            ProWorkProcess existing = getByIdOrThrow(id);
            WorkProcessStatus currentStatus = WorkProcessStatus.fromCode(existing.getStatus());

            // 2. 状态转换校验：只允许从 PENDING（待开始）→ CANCELLED（已取消）
            requireProcessTransition(currentStatus, WorkProcessStatus.CANCELLED, id);

            // 3. 乐观锁更新：状态改为 CANCELLED
            optimisticUpdateStatusOnly(id, existing.getVersion(), WorkProcessStatus.CANCELLED.getCode());

            // 4. 记录取消日志
            statusTransitionLogService.logTransition("WORK_PROCESS", id,
                    currentStatus.getCode(), WorkProcessStatus.CANCELLED.getCode(), "cancel", remark);

            log.info("取消工序：processRecordId={}, remark={}", id, remark);
            ProWorkProcessVO vo = getByIdAsVO(id);
            markIdempotencySuccess(vo);
            return vo;
        } catch (Exception e) {
            markIdempotencyFailure();
            throw e;
        }
    }

    // ==================== 私有辅助方法 ====================

    private ProWorkProcess getByIdOrThrow(Long id) {
        ProWorkProcess entity = proWorkProcessMapper.selectById(id);
        if (entity == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        return entity;
    }

    private ProWorkProcessVO getByIdAsVO(Long id) {
        // 使用 JOIN sys_user 的查询，确保 operator 显示名正确
        ProWorkProcess entity = proWorkProcessMapper.selectByIdWithOperator(id);
        return toVO(entity);
    }

    /**
     * 校验工单是否处于可生产状态。
     */
    private void validateWorkOrderActive(Long workOrderId) {
        ProWorkOrder order = proWorkOrderMapper.selectById(workOrderId);
        if (order == null) {
            throw new BaseException("工单不存在：workOrderId=" + workOrderId);
        }
        WorkOrderStatus orderStatus = WorkOrderStatus.fromCode(order.getStatus());
        if (orderStatus == WorkOrderStatus.PAUSED) {
            throw new StateMachineException("工单已暂停，请先恢复后再操作");
        }
        if (!orderStatus.isProductionActive()) {
            throw new StateMachineException(
                    String.format("工单[%d]当前状态为[%s]，不允许生产操作", workOrderId, orderStatus.getLabel()));
        }
    }

    /**
     * 校验前序工序是否已完成。
     * 前序工序不存在时必须抛异常，不能静默跳过。
     */
    private void validatePredecessorCompletion(Long workOrderId, int stepSeq) {
        if (stepSeq <= 1) return;

        ProWorkProcess predecessor = getOne(new LambdaQueryWrapper<ProWorkProcess>()
                .eq(ProWorkProcess::getWorkOrderId, workOrderId)
                .eq(ProWorkProcess::getStepSeq, stepSeq - 1));

        if (predecessor == null) {
            throw new StateMachineException(String.format(
                    "前序工序不存在：workOrderId=%d, stepSeq=%d", workOrderId, stepSeq - 1));
        }

        WorkProcessStatus predStatus = WorkProcessStatus.fromCode(predecessor.getStatus());
        if (predStatus != WorkProcessStatus.COMPLETED && predStatus != WorkProcessStatus.SKIPPED) {
            throw new StateMachineException(MessageConstant.PREDECESSOR_NOT_COMPLETED);
        }
    }

    /**
     * 校验工序读取归属：非 view-all 用户只能读取自己工单的工序。
     * Controller 层会先检查 view-all 权限，无权限时才调用此方法。
     */
    @Override
    public void validateProcessOwnershipForRead(Long processRecordId) {
        ProWorkProcess process = proWorkProcessMapper.selectById(processRecordId);
        if (process == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        ProWorkOrder order = proWorkOrderMapper.selectById(process.getWorkOrderId());
        if (order == null) {
            throw new BaseException("工单不存在：workOrderId=" + process.getWorkOrderId());
        }
        Long currentUserId = BaseContext.getCurrentId();
        if (!currentUserId.equals(order.getAssigneeUserId())) {
            throw new BaseException("您不是该工单的执行人，无权查看此工序");
        }
    }

    /**
     * 校验当前用户是否是该工序所属工单的执行人。
     * 拥有 view-all 权限的用户由 Controller 层放行，此处仅做生产操作员的归属校验。
     */
    private void validateProcessOwnership(ProWorkProcess process) {
        ProWorkOrder order = proWorkOrderMapper.selectById(process.getWorkOrderId());
        if (order == null) {
            throw new BaseException("工单不存在：workOrderId=" + process.getWorkOrderId());
        }
        Long currentUserId = BaseContext.getCurrentId();
        if (!currentUserId.equals(order.getAssigneeUserId())) {
            throw new BaseException("您不是该工单的执行人，无权操作此工序");
        }
    }

    /**
     * [答辩重点] 自动推进下一道工序 —— 工序流转的核心联动逻辑。
     * [状态变化] 下一道工序 PENDING → READY（标记为可开工）
     * 如果没有下一道工序 → 调用 autoCompleteWorkOrderIfAllDone 判断工单是否可以自动完工
     */
    private void advanceToNextProcess(Long workOrderId, int completedStepSeq) {
        ProWorkProcess next = getOne(new LambdaQueryWrapper<ProWorkProcess>()
                .eq(ProWorkProcess::getWorkOrderId, workOrderId)
                .eq(ProWorkProcess::getStepSeq, completedStepSeq + 1));

        if (next != null) {
            if (WorkProcessStatus.PENDING.getCode().equals(next.getStatus())) {
                optimisticUpdateStatusOnly(next.getProcessRecordId(), next.getVersion(),
                        WorkProcessStatus.READY.getCode());

                statusTransitionLogService.logTransition("WORK_PROCESS", next.getProcessRecordId(),
                        WorkProcessStatus.PENDING.getCode(), WorkProcessStatus.READY.getCode(),
                        "mark_ready", "前序工序完成自动触发");
                log.info("自动标记下一道工序可开工：processRecordId={}, stepSeq={}",
                        next.getProcessRecordId(), completedStepSeq + 1);
            }
        } else {
            // 没有下一道工序 — 判断工单所有工序是否都已终端
            autoCompleteWorkOrderIfAllDone(workOrderId);
        }
    }

    /**
     * 如果工单所有工序均为 COMPLETED 或 SKIPPED，自动完工。
     * CANCELLED 工序不算正常完成——存在任何 CANCELLED、REJECTED、WAIT_INSPECTION 等状态时拒绝自动完工。
     */
    private void autoCompleteWorkOrderIfAllDone(Long workOrderId) {
        List<ProWorkProcess> allProcesses = list(new LambdaQueryWrapper<ProWorkProcess>()
                .eq(ProWorkProcess::getWorkOrderId, workOrderId));

        if (allProcesses.isEmpty()) {
            return;
        }

        for (ProWorkProcess p : allProcesses) {
            String statusCode = p.getStatus();
            if (!WorkProcessStatus.COMPLETED.getCode().equals(statusCode)
                    && !WorkProcessStatus.SKIPPED.getCode().equals(statusCode)) {
                // 存在非 COMPLETED/SKIPPED 的工序，不能自动完工
                log.debug("工单[{}]存在{}状态的工序[{}]，不触发自动完工",
                        workOrderId, statusCode, p.getProcessRecordId());
                return;
            }
        }

        proWorkOrderServiceImpl.onLastProcessCompleted(workOrderId);
    }

    /**
     * 从工艺路线 steps_json 中解析工序的顺序号。
     * 优先级：
     * 1. steps_json 中明确标注的 seq
     * 2. steps_json 数组下标 + 1
     * 3. 数据库当前工单最大 step_seq + 1
     * 4. 安全默认值 1
     */
    private int resolveStepSeq(Long workOrderId, Long processId) {
        try {
            ProWorkOrder order = proWorkOrderMapper.selectById(workOrderId);
            if (order == null || order.getRouteId() == null) {
                return fallbackStepSeq(workOrderId);
            }

            BaseRoute route = baseRouteMapper.selectById(order.getRouteId());
            if (route == null || route.getStepsJson() == null || route.getStepsJson().isBlank()) {
                return fallbackStepSeq(workOrderId);
            }

            JsonNode steps = objectMapper.readTree(route.getStepsJson());
            if (!steps.isArray() || steps.size() == 0) {
                return fallbackStepSeq(workOrderId);
            }

            for (int i = 0; i < steps.size(); i++) {
                JsonNode step = steps.get(i);
                if (step.has("processId")
                        && step.get("processId").asLong() == processId.longValue()) {
                    if (step.has("seq")) {
                        JsonNode seqNode = step.get("seq");
                        int seq;
                        if (seqNode.isInt()) {
                            seq = seqNode.asInt();
                        } else if (seqNode.isTextual()) {
                            try {
                                seq = Integer.parseInt(seqNode.asText());
                            } catch (NumberFormatException e) {
                                log.warn("stepSeq 为非数字字符串，使用数组下标：workOrderId={}, processId={}, seqText={}",
                                        workOrderId, processId, seqNode.asText());
                                return i + 1;
                            }
                        } else {
                            log.warn("stepSeq 类型不支持，使用数组下标：workOrderId={}, processId={}",
                                    workOrderId, processId);
                            return i + 1;
                        }
                        if (seq <= 0) {
                            log.warn("stepSeq <= 0，使用数组下标：workOrderId={}, processId={}, seq={}",
                                    workOrderId, processId, seq);
                            return i + 1;
                        }
                        return seq;
                    }
                    // 无 seq 字段，使用数组下标 + 1
                    return i + 1;
                }
            }
            // 工序不在 steps_json 中
            log.info("工序 {} 不在工艺路线的 steps_json 中，使用 fallback：workOrderId={}",
                    processId, workOrderId);
        } catch (Exception e) {
            log.warn("解析工艺路线工序顺序失败：workOrderId={}, processId={}", workOrderId, processId, e);
        }
        return fallbackStepSeq(workOrderId);
    }

    /**
     * 安全兜底：查询当前工单最大 step_seq，返回其 + 1。
     * 如果没有其他工序，返回 1。
     */
    private int fallbackStepSeq(Long workOrderId) {
        try {
            int maxSeq = proWorkProcessMapper.selectMaxStepSeqByWorkOrderId(workOrderId);
            return Math.max(1, maxSeq + 1);
        } catch (Exception e) {
            log.warn("查询最大 step_seq 失败，使用默认值 1：workOrderId={}", workOrderId, e);
            return 1;
        }
    }

    // ==================== 乐观锁更新方法 ====================

    private void optimisticUpdateStatusOnly(Long id, Integer expectedVersion, String newStatus) {
        ProWorkProcess update = new ProWorkProcess();
        update.setProcessRecordId(id);
        update.setStatus(newStatus);
        update.setVersion(expectedVersion);
        int rows = proWorkProcessMapper.updateById(update);
        if (rows == 0) {
            throw new StateMachineException(MessageConstant.DATA_CONCURRENT_MODIFIED);
        }
    }

    private void optimisticUpdate(Long id, Integer expectedVersion,
                                   String newStatus, LocalDateTime startTime, Long operatorUserId) {
        ProWorkProcess update = new ProWorkProcess();
        update.setProcessRecordId(id);
        update.setStatus(newStatus);
        if (startTime != null) update.setStartTime(startTime);
        if (operatorUserId != null) update.setOperatorUserId(operatorUserId);
        update.setVersion(expectedVersion);
        int rows = proWorkProcessMapper.updateById(update);
        if (rows == 0) {
            throw new StateMachineException(MessageConstant.DATA_CONCURRENT_MODIFIED);
        }
    }

    /**
     * [答辩重点] 带报工数量的乐观锁更新 —— 两步操作保护并发报工。
     * [并发控制] 第一步：accumulateReportQty 使用原子 SQL（UPDATE SET report_qty = report_qty + #{qty} WHERE version = ?）
     *   在 SQL 层面保证报工数量的原子累加，同时校验不超过计划数量
     * [并发控制] 第二步：重新读取 version（第一步已 +1）后再更新状态字段
     */
    private void optimisticUpdateWithReport(Long id, Integer expectedVersion,
                                             String newStatus, BigDecimal reportQty,
                                             String qcResult, String qcResultNote) {
        // [并发控制] 使用原子 SQL 累加报工数量，同时校验不超过计划数量
        if (reportQty != null && reportQty.compareTo(BigDecimal.ZERO) > 0) {
            int rows = proWorkProcessMapper.accumulateReportQty(id, expectedVersion, reportQty);
            if (rows == 0) {
                // 区分冲突原因：重新查询验证
                ProWorkProcess current = proWorkProcessMapper.selectById(id);
                if (current == null) {
                    throw new StateMachineException(MessageConstant.DATA_NOT_FOUND);
                }
                // 版本冲突或超量
                if (!expectedVersion.equals(current.getVersion())) {
                    throw new StateMachineException(MessageConstant.DATA_CONCURRENT_MODIFIED);
                }
                throw new StateMachineException("报工数量超出计划数量或工序状态已变更，请刷新后重试");
            }
        }

        // 然后更新状态和其它字段
        ProWorkProcess update = new ProWorkProcess();
        update.setProcessRecordId(id);
        update.setStatus(newStatus);
        update.setEndTime(LocalDateTime.now());
        if (qcResult != null) update.setQcResult(qcResult);
        if (qcResultNote != null) update.setQcResultNote(qcResultNote);
        // 注意：此时 version 已被 accumulateReportQty + 1，
        // 需要重新读取 version 或使用条件较宽松的 update
        ProWorkProcess refreshed = proWorkProcessMapper.selectById(id);
        update.setVersion(refreshed.getVersion());
        int statusRows = proWorkProcessMapper.updateById(update);
        if (statusRows == 0) {
            throw new StateMachineException(MessageConstant.DATA_CONCURRENT_MODIFIED);
        }
    }

    private void optimisticUpdateWithQc(Long id, Integer expectedVersion,
                                         String newStatus, String qcResult, String qcResultNote) {
        ProWorkProcess update = new ProWorkProcess();
        update.setProcessRecordId(id);
        update.setStatus(newStatus);
        update.setQcResult(qcResult);
        if (qcResultNote != null) update.setQcResultNote(qcResultNote);
        update.setVersion(expectedVersion);
        int rows = proWorkProcessMapper.updateById(update);
        if (rows == 0) {
            throw new StateMachineException(MessageConstant.DATA_CONCURRENT_MODIFIED);
        }
    }

    private void optimisticUpdateEntity(Long id, Integer expectedVersion, ProWorkProcess updateData) {
        updateData.setProcessRecordId(id);
        updateData.setVersion(expectedVersion);
        int rows = proWorkProcessMapper.updateById(updateData);
        if (rows == 0) {
            throw new StateMachineException(MessageConstant.DATA_CONCURRENT_MODIFIED);
        }
    }

    /**
     * 检查工序是否有启用的质检项。
     */
    private boolean hasInspectionItems(Long processId) {
        Long count = qmsInspectionItemMapper.selectCount(
                new LambdaQueryWrapper<QmsInspectionItem>()
                        .eq(QmsInspectionItem::getProcessId, processId)
                        .eq(QmsInspectionItem::getStatus, "0"));
        return count != null && count > 0;
    }

    /**
     * 自动创建质检任务（在工序进入 WAIT_INSPECTION 时调用）。
     * [业务规则] 同一次待检周期不重复创建；insert 失败时工序回滚（同一事务）。
     * [并发控制] 唯一索引 uk_qms_active_by_record (active_task_key)
     *   保证同一工序同一时刻只有一张活动质检单。selectCount 仅做快速跳过，实际并发保护依靠数据库约束。
     * [轮次机制] 查询该工序历史质检单的最大轮次，新质检单轮次 = maxRound + 1（初检=1）。
     *   返工完成后的新一轮质检可以创建新的 PENDING 记录，轮次递增。
     * inspectionNo 使用纳秒时间戳 + 随机数保证并发唯一。
     */
    private void autoCreateInspection(Long workOrderId, Long processId, Long processRecordId) {
        // 快速跳过：已有活动质检单（同一processRecordId下status=0待检或status=1已录入）
        Long existingCount = qmsInspectionMapper.selectCount(
                new LambdaQueryWrapper<QmsInspection>()
                        .eq(QmsInspection::getProcessRecordId, processRecordId)
                        .in(QmsInspection::getStatus,
                                InspectionStatus.PENDING.getCode(), InspectionStatus.ENTERED.getCode()));
        if (existingCount != null && existingCount > 0) {
            log.info("已存在活动质检单（待检或已录入），跳过重复创建：processRecordId={}", processRecordId);
            return;
        }

        // 确定质检轮次和父质检单：查询该工序历史最大轮次
        int newRound = 1;
        Long parentInspectionId = null;
        LambdaQueryWrapper<QmsInspection> historyWrapper = new LambdaQueryWrapper<>();
        historyWrapper.eq(QmsInspection::getProcessRecordId, processRecordId)
                      .orderByDesc(QmsInspection::getInspectionRound)
                      .last("LIMIT 1");
        QmsInspection lastInspection = qmsInspectionMapper.selectOne(historyWrapper);
        if (lastInspection != null) {
            newRound = (lastInspection.getInspectionRound() != null ? lastInspection.getInspectionRound() : 0) + 1;
            parentInspectionId = lastInspection.getInspectionId();
            log.info("检测到历史质检单，创建复检单：processRecordId={}, newRound={}, parentInspectionId={}",
                    processRecordId, newRound, parentInspectionId);
        }

        QmsInspection inspection = new QmsInspection();
        inspection.setInspectionNo(generateUniqueInspectionNo());
        inspection.setWorkOrderId(workOrderId);
        inspection.setProcessId(processId);
        inspection.setProcessRecordId(processRecordId);
        inspection.setResult("0");
        inspection.setDetailsJson("[]");
        inspection.setInspector(null);
        inspection.setInspectorUserId(null);
        inspection.setInspectionTime(null);
        inspection.setStatus(InspectionStatus.PENDING.getCode());
        inspection.setInspectionRound(newRound);
        inspection.setParentInspectionId(parentInspectionId);
        inspection.setVersion(0);
        inspection.setCreateTime(LocalDateTime.now());

        // PostgreSQL 在唯一约束冲突后会把整个事务标记为 aborted，即使 Java 层捕获了异常，
        // 后续语句也会报 "current transaction is aborted"。因此把插入放进保存点（NESTED），
        // 冲突时只回滚该保存点，外层业务事务仍可继续；无外层事务时等价于 REQUIRED。
        // 事务管理器未注入（单元测试直接 new 出实例）时退化为直接插入，保持可测试性。
        if (transactionManager == null) {
            qmsInspectionMapper.insert(inspection);
            log.info("自动创建质检单成功(无事务管理器直插)：inspectionId={}, processRecordId={}",
                    inspection.getInspectionId(), processRecordId);
            return;
        }
        TransactionTemplate savepoint = new TransactionTemplate(transactionManager);
        savepoint.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
        try {
            savepoint.executeWithoutResult(ignored -> qmsInspectionMapper.insert(inspection));
            log.info("自动创建质检单成功：inspectionId={}, inspectionNo={}, processRecordId={}, round={}",
                    inspection.getInspectionId(), inspection.getInspectionNo(), processRecordId, newRound);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            // 仅活动任务唯一索引冲突 → 业务提示；其他冲突（如inspection_no）→ 重新抛出
            if (msg.contains("uk_qms_active_by_record") || msg.contains("active_task_key")) {
                log.info("并发创建质检单被活动唯一约束拦截：processRecordId={}", processRecordId);
            } else {
                log.error("质检单创建失败(非活动任务冲突)：processRecordId={}, msg={}", processRecordId, msg);
                throw e;
            }
        }
    }

    /**
     * 生成唯一质检单号：QC + yyyyMMddHHmmss + 纳秒后6位 + 2位随机数。
     */
    private String generateUniqueInspectionNo() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        String timestamp = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String nano = String.format("%06d", now.getNano() / 1000 % 1000000);
        String rand = String.format("%02d", (int) (Math.random() * 100));
        return "QC" + timestamp + nano + rand;
    }

    /**
     * 封装工序状态转换校验，不合法时抛出 StateMachineException。
     */
    private static void requireProcessTransition(WorkProcessStatus from, WorkProcessStatus to, Long entityId) {
        if (!StatusTransitionValidator.canTransition(from, to)) {
            throw new StateMachineException(
                    String.format("工序[%d]不允许从[%s]流转到[%s]", entityId, from.getLabel(), to.getLabel()));
        }
    }

    // ==================== 校验与转换 ====================

    private void checkBusinessRules(ProWorkProcessDTO dto) {
        if (dto.getEquipmentId() != null && baseEquipmentMapper.selectById(dto.getEquipmentId()) == null) {
            throw new BaseException("设备不存在");
        }
    }

    private ProWorkProcess toEntity(ProWorkProcessDTO dto) {
        ProWorkProcess entity = new ProWorkProcess();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }

    private ProWorkProcessVO toVO(ProWorkProcess entity) {
        ProWorkProcessVO vo = new ProWorkProcessVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    // ==================== 幂等保护辅助方法 ====================

    /**
     * 检查并执行幂等保护。仅当 BaseContext 中存在 Idempotency-Key 时生效。
     * 内部质检回调由父质检请求保护，不重复申请执行权。
     *
     * @return null 表示需要继续执行业务；否则返回缓存的结果
     */
    private ProWorkProcessVO checkIdempotencyAndProceed(String action,
                                                         java.util.Map<String, Object> requestParams,
                                                         Long businessId) {
        String idempotencyKey = BaseContext.getIdempotencyKey();
        if (idempotencyKey == null || BaseContext.getIdempotencyRecordId() != null) {
            // 内部调用，跳过幂等
            return null;
        }

        Long userId = BaseContext.getCurrentId();
        IdempotentResult result = idempotencyService.tryAcquire(
                idempotencyKey, IdempotencyService.BIZ_TYPE_WORK_PROCESS,
                action, requestParams, userId);

        if (result.isDuplicate()) {
            try {
                return objectMapper.readValue(result.getExistingResponse(), ProWorkProcessVO.class);
            } catch (Exception e) {
                log.error("解析幂等缓存响应失败：recordId={}", result.getRecordId(), e);
                throw new BaseException("系统繁忙，请稍后重试");
            }
        }

        if (result.isProcessing()) {
            throw new BaseException(result.getExistingResponse());
        }

        // ACQUIRED：暂存 recordId 用于后续标记成功/失败
        idempotencyService.lockExecution(result.getRecordId());
        BaseContext.setIdempotencyRecordId(result.getRecordId());
        return null;
    }

    /**
     * 标记幂等记录成功。
     */
    private void markIdempotencySuccess(ProWorkProcessVO vo) {
        Long recordId = BaseContext.getIdempotencyRecordId();
        if (recordId == null) return;
        try {
            String responseJson = objectMapper.writeValueAsString(vo);
            idempotencyService.markSuccess(recordId, vo.getProcessRecordId(), responseJson);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("保存幂等响应失败：recordId={}", recordId, e);
            throw new BaseException("无法保存请求结果，请稍后重试");
        }
    }

    private void markIdempotencyFailure() {
        Long recordId = BaseContext.getIdempotencyRecordId();
        if (recordId != null) idempotencyService.markFailure(recordId, 0, "business_error");
    }
}
