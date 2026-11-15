package com.bikeassembly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.context.BaseContext;
import com.bikeassembly.dto.ProWorkOrderDTO;
import com.bikeassembly.dto.message.WorkOrderMessageDTO;
import com.bikeassembly.entity.BaseProcess;
import com.bikeassembly.entity.BaseRoute;
import com.bikeassembly.entity.ProWorkOrder;
import com.bikeassembly.entity.ProWorkProcess;
import com.bikeassembly.entity.SysRole;
import com.bikeassembly.entity.SysUser;
import com.bikeassembly.enums.StatusTransitionValidator;
import com.bikeassembly.enums.WorkOrderStatus;
import com.bikeassembly.enums.WorkProcessStatus;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.exception.StateMachineException;
import com.bikeassembly.mapper.BaseItemMapper;
import com.bikeassembly.mapper.BaseProcessMapper;
import com.bikeassembly.mapper.BaseRouteMapper;
import com.bikeassembly.mapper.BaseWorkUnitMapper;
import com.bikeassembly.mapper.ProWorkOrderMapper;
import com.bikeassembly.mapper.ProWorkProcessMapper;
import com.bikeassembly.mapper.SysRoleMapper;
import com.bikeassembly.mapper.SysUserMapper;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.service.IdempotencyService;
import com.bikeassembly.service.IdempotentResult;
import com.bikeassembly.service.MessageProducerService;
import com.bikeassembly.service.erp.ErpSyncPublisher;
import com.bikeassembly.ws.WebSocketNotifier;
import com.bikeassembly.ws.WsMessageDTO;
import com.bikeassembly.service.ProWorkOrderService;
import com.bikeassembly.service.StatusTransitionLogService;
import com.bikeassembly.utils.ScopeValidator;
import com.bikeassembly.vo.ProWorkOrderVO;
import com.bikeassembly.vo.SimpleUserVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ProWorkOrderServiceImpl extends ServiceImpl<ProWorkOrderMapper, ProWorkOrder> implements ProWorkOrderService {

    @Autowired
    private ProWorkOrderMapper proWorkOrderMapper;
    @Autowired
    private BaseItemMapper baseItemMapper;
    @Autowired
    private BaseWorkUnitMapper baseWorkUnitMapper;
    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private SysRoleMapper sysRoleMapper;
    @Autowired
    private BaseRouteMapper baseRouteMapper;
    @Autowired
    private BaseProcessMapper baseProcessMapper;
    @Autowired
    private ProWorkProcessMapper proWorkProcessMapper;
    @Autowired
    private ObjectProvider<MessageProducerService> messageProducerProvider;
    @Autowired
    private StatusTransitionLogService statusTransitionLogService;
    @Autowired
    private IdempotencyService idempotencyService;
    @Autowired
    private WebSocketNotifier webSocketNotifier;
    @Autowired
    private ErpSyncPublisher erpSyncPublisher;

    @Autowired
    private ObjectMapper objectMapper;

    // ==================== 基础 CRUD ====================

    /**
     * [答辩入口] 新增工单。
     * [业务规则] 1. 检查工单号唯一性 → 2. 验证物料和外键存在 → 3. 设置默认状态为 CREATED（"0"） → 4. 插入数据库 → 5. 发送 RabbitMQ 消息
     * [状态变化] status 初始值为 "0"（已创建/CREATED）
     * [消息发送] 新增成功后通过 MessageProducerService 发送 WORK_ORDER_CREATED 事件（发送失败不影响主业务）
     */
    @Override
    public ProWorkOrderVO saveProWorkOrder(ProWorkOrderDTO dto) {
        checkWorkOrderNoUnique(dto.getWorkOrderNo(), null);

        // 校验物料存在
        if (dto.getItemId() != null
                && baseItemMapper.selectById(dto.getItemId()) == null) {
            throw new BaseException("物料不存在");
        }
        // 校验计划数量
        if (dto.getPlanQty() == null || dto.getPlanQty().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new BaseException("计划数量必须大于0");
        }
        // 校验工作单元
        if (dto.getWorkUnitId() != null
                && baseWorkUnitMapper.selectById(dto.getWorkUnitId()) == null) {
            throw new BaseException("工作单元不存在");
        }
        // 校验工艺路线：存在、启用、产品匹配
        if (dto.getRouteId() != null) {
            BaseRoute route = baseRouteMapper.selectById(dto.getRouteId());
            if (route == null) {
                throw new BaseException("工艺路线不存在：routeId=" + dto.getRouteId());
            }
            if (!"0".equals(route.getStatus())) {
                throw new BaseException("工艺路线已禁用，无法创建工单");
            }
            if (dto.getItemId() != null && route.getItemId() != null
                    && !route.getItemId().equals(dto.getItemId())) {
                throw new BaseException("工艺路线所属产品与工单产品不一致");
            }
        }

        ProWorkOrder entity = toEntity(dto);
        // 新增工单强制设置为已创建状态，忽略前端传入的 status
        entity.setStatus(WorkOrderStatus.CREATED.getCode());
        entity.setVersion(0);
        // 数据库维护字段：前端不可传入
        entity.setActualStartTime(null);
        entity.setActualEndTime(null);
        proWorkOrderMapper.insert(entity);
        log.info("新增生产工单成功：workOrderId={}, workOrderNo={}", entity.getWorkOrderId(), entity.getWorkOrderNo());

        // 工单创建通知（MQ 优先，未启用时降级同步 WebSocket）
        notifyWorkOrder(entity.getWorkOrderId(), WsMessageDTO.EVENT_CREATE);
        return toVO(entity);
    }

    @Override
    public ProWorkOrderVO updateProWorkOrder(Long id, ProWorkOrderDTO dto) {
        ProWorkOrder existing = proWorkOrderMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }

        // CREATED 状态外禁止修改关键业务字段（物料、路线、计划数量、工作单元）
        WorkOrderStatus currentStatus = WorkOrderStatus.fromCode(existing.getStatus());
        boolean isCreated = currentStatus == WorkOrderStatus.CREATED;
        if (!isCreated) {
            if (dto.getItemId() != null && !dto.getItemId().equals(existing.getItemId())) {
                throw new BaseException("已下达/生产中/已完工的工单不可修改物料");
            }
            if (dto.getRouteId() != null && !dto.getRouteId().equals(existing.getRouteId())) {
                throw new BaseException("已下达/生产中/已完工的工单不可修改工艺路线");
            }
            if (dto.getPlanQty() != null && dto.getPlanQty().compareTo(existing.getPlanQty()) != 0) {
                throw new BaseException("已下达/生产中/已完工的工单不可修改计划数量");
            }
            if (dto.getWorkUnitId() != null && !dto.getWorkUnitId().equals(existing.getWorkUnitId())) {
                throw new BaseException("已下达/生产中/已完工的工单不可修改工作单元");
            }
        }

        checkWorkOrderNoUnique(dto.getWorkOrderNo(), id);

        if (dto.getItemId() != null
                && baseItemMapper.selectById(dto.getItemId()) == null) {
            throw new BaseException("物料不存在");
        }
        if (dto.getWorkUnitId() != null
                && baseWorkUnitMapper.selectById(dto.getWorkUnitId()) == null) {
            throw new BaseException("工作单元不存在");
        }

        // 计算最终 itemId 和 routeId（取 DTO 新值，无变化时用原值）
        Long finalItemId = dto.getItemId() != null ? dto.getItemId() : existing.getItemId();
        Long finalRouteId = dto.getRouteId() != null ? dto.getRouteId() : existing.getRouteId();

        // itemId 或 routeId 任一变化时，按最终值联合校验路线
        boolean itemChanged = dto.getItemId() != null && !dto.getItemId().equals(existing.getItemId());
        boolean routeChanged = dto.getRouteId() != null && !dto.getRouteId().equals(existing.getRouteId());
        if ((itemChanged || routeChanged) && finalRouteId != null) {
            validateRouteForEditing(finalRouteId, finalItemId);
        }

        ProWorkOrder entity = toEntity(dto);
        entity.setWorkOrderId(id);
        entity.setVersion(existing.getVersion());
        // 编辑基础信息时不得直接修改状态，状态只能通过下达/开工/完工/关闭等业务接口变更
        entity.setStatus(existing.getStatus());
        entity.setAssigneeUserId(existing.getAssigneeUserId());
        // 保护数据库维护字段：不允许前端覆盖
        entity.setActualStartTime(existing.getActualStartTime());
        entity.setActualEndTime(existing.getActualEndTime());
        int rows = proWorkOrderMapper.updateById(entity);
        if (rows == 0) {
            throw new StateMachineException(MessageConstant.DATA_CONCURRENT_MODIFIED);
        }
        log.info("修改生产工单成功：workOrderId={}", id);

        ProWorkOrder updated = proWorkOrderMapper.selectById(id);
        return toVO(updated);
    }

    @Override
    public ProWorkOrderVO getProWorkOrderById(Long id) {
        ProWorkOrder entity = proWorkOrderMapper.selectById(id);
        if (entity == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        return toVO(entity);
    }

    /**
     * [答辩入口] 工单分页查询。
     * [核心业务] 支持两种查询模式：
     *   1. 无条件时使用 MyBatis-Plus 默认分页
     *   2. 有条件时（orderNo/productName/指派给我）使用自定义 XML 分页 SQL
     * [数据库操作] 分页查询 pro_work_order 表 LEFT JOIN base_item 表
     * [数据转换] Entity → VO 通过 BeanUtils.copyProperties
     * [权限控制] scope="mine" 时过滤当前登录用户被指派的工单
     *
     * Service 层作为最终安全边界：再次校验 scope 合法性，防止 Controller 被绕过。
     */
    @Override
    public PageResult<ProWorkOrderVO> listProWorkOrders(Long page, Long size,
            String workOrderNo, String orderNo, String productName, String scope) {
        // Service 层最终安全边界：重新校验 scope 合法性
        scope = ScopeValidator.requireValid(scope, ScopeValidator.MINE_OR_ALL);
        boolean isMine = "mine".equals(scope);
        boolean hasFilter = (workOrderNo != null && !workOrderNo.isBlank())
                || (orderNo != null && !orderNo.isBlank())
                || (productName != null && !productName.isBlank())
                || isMine;
        Page<ProWorkOrder> pageParam = new Page<>(page, size);
        IPage<ProWorkOrder> pageResult;

        if (hasFilter) {
            Long currentUserId = null;
            if (isMine) {
                currentUserId = BaseContext.getCurrentId();
            }
            pageResult = proWorkOrderMapper.selectPageByCondition(
                    pageParam, workOrderNo, orderNo, productName, currentUserId);
        } else {
            // scope=all 且无其他筛选条件：查询全部（Controller 已校验 all 权限）
            pageResult = proWorkOrderMapper.selectPage(pageParam, null);
        }
        List<ProWorkOrderVO> records = pageResult.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResult.of(records, pageResult.getTotal(), pageResult.getCurrent(), pageResult.getSize());
    }

    @Override
    public void deleteProWorkOrder(Long id) {
        ProWorkOrder existing = proWorkOrderMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }

        int refCount = proWorkOrderMapper.countReferences(id);
        if (refCount > 0) {
            throw new BaseException("该生产工单被 " + refCount + " 条业务数据引用，无法删除");
        }

        proWorkOrderMapper.deleteById(id);
        log.info("删除生产工单成功：workOrderId={}", id);
    }

    // ==================== 执行人指派 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProWorkOrderVO assignAssignee(Long workOrderId, Long userId) {
        // [幂等保护]
        java.util.LinkedHashMap<String, Object> idmParams = new java.util.LinkedHashMap<>();
        idmParams.put("workOrderId", workOrderId);
        idmParams.put("userId", userId);
        ProWorkOrderVO cachedVo = checkWorkOrderIdempotency(
                IdempotencyService.ACTION_ASSIGN, idmParams, workOrderId);
        if (cachedVo != null) {
            return cachedVo;
        }

        try {
            ProWorkOrder existing = proWorkOrderMapper.selectById(workOrderId);
            if (existing == null) {
                throw new BaseException(MessageConstant.DATA_NOT_FOUND);
            }
            WorkOrderStatus status = WorkOrderStatus.fromCode(existing.getStatus());
            // 仅 CREATED/RELEASED 状态可指派（生产中/暂停中不可通过普通指派改人）
            if (status != WorkOrderStatus.CREATED && status != WorkOrderStatus.RELEASED) {
                throw new BaseException("当前状态[" + status.getLabel() + "]不可指派执行人");
            }
            if (existing.getAssigneeUserId() != null) {
                throw new BaseException("该工单已指派执行人，请使用更换操作");
            }

            SysUser user = sysUserMapper.selectById(userId);
            if (user == null) {
                throw new BaseException("用户不存在");
            }
            if ("1".equals(user.getStatus())) {
                throw new BaseException("禁用的用户不可被指派");
            }

            ProWorkOrder entity = new ProWorkOrder();
            entity.setWorkOrderId(workOrderId);
            entity.setAssigneeUserId(userId);
            entity.setVersion(existing.getVersion());
            int rows = proWorkOrderMapper.updateById(entity);
            if (rows == 0) {
                throw new StateMachineException(MessageConstant.DATA_CONCURRENT_MODIFIED);
            }
            log.info("指派执行人成功：workOrderId={}, assigneeUserId={}", workOrderId, userId);

            ProWorkOrder updated = proWorkOrderMapper.selectById(workOrderId);
            ProWorkOrderVO vo = toVO(updated);
            markWorkOrderIdempotencySuccess(vo);
            return vo;
        } catch (Exception e) {
            markWorkOrderIdempotencyFailure();
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProWorkOrderVO reassignAssignee(Long workOrderId, Long userId) {
        // [幂等保护]
        java.util.LinkedHashMap<String, Object> idmParams = new java.util.LinkedHashMap<>();
        idmParams.put("workOrderId", workOrderId);
        idmParams.put("userId", userId);
        ProWorkOrderVO cachedVo = checkWorkOrderIdempotency(
                IdempotencyService.ACTION_REASSIGN, idmParams, workOrderId);
        if (cachedVo != null) {
            return cachedVo;
        }

        try {
            ProWorkOrder existing = proWorkOrderMapper.selectById(workOrderId);
            if (existing == null) {
                throw new BaseException(MessageConstant.DATA_NOT_FOUND);
            }
            WorkOrderStatus status = WorkOrderStatus.fromCode(existing.getStatus());
            // 仅 CREATED/RELEASED 状态可改派（生产中不可改派）
            if (status != WorkOrderStatus.CREATED && status != WorkOrderStatus.RELEASED) {
                throw new BaseException("当前状态[" + status.getLabel() + "]不可更换执行人");
            }

            SysUser user = sysUserMapper.selectById(userId);
            if (user == null) {
                throw new BaseException("用户不存在");
            }
            if ("1".equals(user.getStatus())) {
                throw new BaseException("禁用的用户不可被指派");
            }

            Long oldAssigneeUserId = existing.getAssigneeUserId();
            ProWorkOrder entity = new ProWorkOrder();
            entity.setWorkOrderId(workOrderId);
            entity.setAssigneeUserId(userId);
            entity.setVersion(existing.getVersion());
            int rows = proWorkOrderMapper.updateById(entity);
            if (rows == 0) {
                throw new StateMachineException(MessageConstant.DATA_CONCURRENT_MODIFIED);
            }
            log.info("更换执行人成功：workOrderId={}, oldAssigneeUserId={}, newAssigneeUserId={}",
                    workOrderId, oldAssigneeUserId, userId);

            ProWorkOrder updated = proWorkOrderMapper.selectById(workOrderId);
            ProWorkOrderVO vo = toVO(updated);
            markWorkOrderIdempotencySuccess(vo);
            return vo;
        } catch (Exception e) {
            markWorkOrderIdempotencyFailure();
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProWorkOrderVO unassignAssignee(Long workOrderId) {
        // [幂等保护]
        java.util.LinkedHashMap<String, Object> idmParams = new java.util.LinkedHashMap<>();
        idmParams.put("workOrderId", workOrderId);
        ProWorkOrderVO cachedVo = checkWorkOrderIdempotency(
                IdempotencyService.ACTION_UNASSIGN, idmParams, workOrderId);
        if (cachedVo != null) {
            return cachedVo;
        }

        try {
            ProWorkOrder existing = proWorkOrderMapper.selectById(workOrderId);
            if (existing == null) {
                throw new BaseException(MessageConstant.DATA_NOT_FOUND);
            }
            WorkOrderStatus status = WorkOrderStatus.fromCode(existing.getStatus());
            // 仅 CREATED/RELEASED 状态可取消指派
            if (status != WorkOrderStatus.CREATED && status != WorkOrderStatus.RELEASED) {
                throw new BaseException("当前状态[" + status.getLabel() + "]不可取消指派");
            }

            ProWorkOrder entity = new ProWorkOrder();
            entity.setWorkOrderId(workOrderId);
            entity.setAssigneeUserId(null);
            entity.setVersion(existing.getVersion());
            int rows = proWorkOrderMapper.updateById(entity);
            if (rows == 0) {
                throw new StateMachineException(MessageConstant.DATA_CONCURRENT_MODIFIED);
            }
            log.info("取消指派成功：workOrderId={}, oldAssigneeUserId={}", workOrderId, existing.getAssigneeUserId());

            ProWorkOrder updated = proWorkOrderMapper.selectById(workOrderId);
            ProWorkOrderVO vo = toVO(updated);
            markWorkOrderIdempotencySuccess(vo);
            return vo;
        } catch (Exception e) {
            markWorkOrderIdempotencyFailure();
            throw e;
        }
    }

    @Override
    public List<SimpleUserVO> getActiveUsersForAssignment() {
        List<SysUser> users = sysUserMapper.selectList(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getStatus, "0")
        );
        List<SimpleUserVO> result = new ArrayList<>();
        for (SysUser user : users) {
            String roleKey = null;
            if (user.getRoleId() != null) {
                SysRole role = sysRoleMapper.selectById(user.getRoleId());
                if (role != null) roleKey = role.getRoleKey();
            }
            result.add(SimpleUserVO.builder()
                    .userId(user.getUserId())
                    .username(user.getUsername())
                    .nickname(user.getNickname())
                    .roleKey(roleKey)
                    .build());
        }
        return result;
    }

    // ==================== 状态机操作 ====================

    /**
     * [答辩入口] 下达工单：CREATED → RELEASED，并根据工艺路线自动生成工序执行记录。
     * [状态变化] status: "0" → "4"
     * [业务规则] 1. 校验工单已指派执行人 → 2. 校验工艺路线存在、启用、产品匹配
     *   → 3. 解析 steps_json → 4. 校验每道工序存在且启用 → 5. 校验 seq 连续无重复
     *   → 6. 所有校验通过后乐观锁更新状态 → 7. 生成工序记录（第一道READY，其余PENDING）
     * [事务] @Transactional：状态更新 + 工序记录生成 + 状态日志 同一事务，任意步骤失败全部回滚
     * [并发控制] 乐观锁更新状态 + 工序记录唯一索引 uk_wp_wo_step (work_order_id, step_seq) 防重复
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProWorkOrderVO releaseWorkOrder(Long workOrderId) {
        // [幂等保护]
        java.util.LinkedHashMap<String, Object> idmParams = new java.util.LinkedHashMap<>();
        idmParams.put("workOrderId", workOrderId);
        ProWorkOrderVO cachedVo = checkWorkOrderIdempotency(
                IdempotencyService.ACTION_RELEASE, idmParams, workOrderId);
        if (cachedVo != null) {
            return cachedVo;
        }

        try {
            ProWorkOrderVO result = doReleaseWorkOrder(workOrderId);
            markWorkOrderIdempotencySuccess(result);
            notifyWorkOrder(workOrderId, WsMessageDTO.EVENT_RELEASE);
            erpSyncPublisher.publish("WORK_ORDER", workOrderId, "O");
            return result;
        } catch (Exception e) {
            markWorkOrderIdempotencyFailure();
            throw e;
        }
    }

    private ProWorkOrderVO doReleaseWorkOrder(Long workOrderId) {
        // 1. 查询工单，获取当前状态
        ProWorkOrder existing = getByIdOrThrow(workOrderId);
        WorkOrderStatus currentStatus = WorkOrderStatus.fromCode(existing.getStatus());

        // 2. 状态转换校验：CREATED（已创建）→ RELEASED（已下达）
        requireWorkOrderTransition(currentStatus, WorkOrderStatus.RELEASED, workOrderId);

        // 3. 校验工单已指派执行人
        if (existing.getAssigneeUserId() == null) {
            throw new BaseException("工单未指派执行人，请先指派后再下达");
        }

        // 4. 校验工单绑定了工艺路线
        if (existing.getRouteId() == null) {
            throw new BaseException("工单未绑定工艺路线，无法下达");
        }

        // 5. 预校验：解析路线、校验工序（在改状态之前，失败时工单仍为 CREATED）
        validateRouteAndProcessesBeforeRelease(existing);

        // 6. 乐观锁更新状态
        optimisticUpdateStatus(workOrderId, existing.getVersion(), WorkOrderStatus.RELEASED.getCode());

        // 7. 生成工序执行记录
        generateProcessRecords(workOrderId, existing.getRouteId());

        // 8. 记录状态转换日志
        statusTransitionLogService.logTransition("WORK_ORDER", workOrderId,
                currentStatus.getCode(), WorkOrderStatus.RELEASED.getCode(), "release", null);

        log.info("下达工单成功：workOrderId={}", workOrderId);
        return getByIdAsVO(workOrderId);
    }

    /**
     * 下达前预校验：路线存在/启用/产品匹配 + 每道工序存在/启用 + seq 合法性。
     * 此方法在状态更新前调用，确保校验失败时工单状态不变。
     */
    /**
     * 编辑阶段工艺路线基础校验：存在、启用、产品匹配、步骤非空。
     * 不校验具体工序存在和seq连续性（那是下达时的校验）。
     */
    private void validateRouteForEditing(Long routeId, Long itemId) {
        BaseRoute route = baseRouteMapper.selectById(routeId);
        if (route == null) {
            throw new BaseException("工艺路线不存在：routeId=" + routeId);
        }
        if (!"0".equals(route.getStatus())) {
            throw new BaseException("工艺路线已禁用，无法使用");
        }
        if (route.getItemId() != null && itemId != null
                && !route.getItemId().equals(itemId)) {
            throw new BaseException("工艺路线所属产品与工单产品不一致");
        }

        String stepsJson = route.getStepsJson();
        if (stepsJson == null || stepsJson.isBlank()) {
            throw new BaseException("工艺路线没有工序明细：routeId=" + routeId);
        }
        try {
            JsonNode steps = objectMapper.readTree(stepsJson);
            if (!steps.isArray() || steps.size() == 0) {
                throw new BaseException("工艺路线没有工序明细：routeId=" + routeId);
            }
        } catch (Exception e) {
            log.error("工艺路线 steps_json 解析失败：routeId={}", routeId, e);
            throw new BaseException("工艺路线 steps_json 解析失败：routeId=" + routeId);
        }
    }

    private void validateRouteAndProcessesBeforeRelease(ProWorkOrder workOrder) {
        Long routeId = workOrder.getRouteId();
        BaseRoute route = baseRouteMapper.selectById(routeId);
        if (route == null) {
            throw new BaseException("工艺路线不存在：routeId=" + routeId);
        }
        if (!"0".equals(route.getStatus())) {
            throw new BaseException("工艺路线已禁用，无法下达工单");
        }
        if (route.getItemId() != null && workOrder.getItemId() != null
                && !route.getItemId().equals(workOrder.getItemId())) {
            throw new BaseException("工艺路线所属产品与工单产品不一致，无法下达");
        }

        String stepsJson = route.getStepsJson();
        if (stepsJson == null || stepsJson.isBlank()) {
            throw new BaseException("工艺路线没有工序明细：routeId=" + routeId);
        }

        JsonNode steps;
        try {
            steps = objectMapper.readTree(stepsJson);
        } catch (Exception e) {
            log.error("工艺路线 steps_json 解析失败：routeId={}", routeId, e);
            throw new BaseException("工艺路线 steps_json 解析失败：routeId=" + routeId);
        }

        if (!steps.isArray() || steps.size() == 0) {
            throw new BaseException("工艺路线没有工序明细：routeId=" + routeId);
        }

        // 收集并校验每道工序的 processId 和 seq
        java.util.Set<Integer> seqSet = new java.util.HashSet<>();
        int maxSeq = 0;
        for (int i = 0; i < steps.size(); i++) {
            JsonNode step = steps.get(i);

            Long processId = resolveProcessIdFromStep(step);
            if (processId == null) {
                throw new BaseException(String.format(
                        "工艺路线第%d步缺少工序标识（需要 processId 或 process_code）：routeId=%d",
                        i + 1, routeId));
            }

            BaseProcess process = baseProcessMapper.selectById(processId);
            if (process == null) {
                throw new BaseException("工序不存在：processId=" + processId);
            }
            if (!"0".equals(process.getStatus())) {
                throw new BaseException("工序已禁用：" + process.getProcessName() + "（processId=" + processId + "）");
            }

            int stepSeq = resolveStepSeqFromStep(step, i);
            if (stepSeq <= 0) {
                throw new BaseException(String.format("工序顺序号必须大于0：stepSeq=%d", stepSeq));
            }
            if (!seqSet.add(stepSeq)) {
                throw new BaseException(String.format("工序顺序号重复：stepSeq=%d", stepSeq));
            }
            if (stepSeq > maxSeq) {
                maxSeq = stepSeq;
            }
        }

        // 校验 seq 从 1 开始连续排列，不允许缺号
        if (maxSeq != steps.size()) {
            throw new BaseException(String.format(
                    "工序顺序号不连续：共%d道工序，最大序号为%d，存在缺号", steps.size(), maxSeq));
        }
    }

    /**
     * 根据工艺路线生成工序执行记录。
     * 前置条件：validateRouteAndProcessesBeforeRelease 已通过。
     * [数据库操作] 逐条插入 pro_work_process 记录，依赖唯一索引 (work_order_id, step_seq) 防并发重复
     */
    private void generateProcessRecords(Long workOrderId, Long routeId) {
        BaseRoute route = baseRouteMapper.selectById(routeId);
        if (route == null) {
            throw new BaseException("工艺路线不存在：routeId=" + routeId);
        }

        String stepsJson = route.getStepsJson();
        JsonNode steps;
        try {
            steps = objectMapper.readTree(stepsJson);
        } catch (Exception e) {
            throw new BaseException("工艺路线 steps_json 解析失败：routeId=" + routeId);
        }

        // 构建步骤列表并按 seq 排序
        java.util.List<JsonNode> stepList = new java.util.ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            stepList.add(steps.get(i));
        }
        stepList.sort((a, b) -> {
            int seqA = resolveStepSeqFromStep(a, 0);
            int seqB = resolveStepSeqFromStep(b, 0);
            return Integer.compare(seqA, seqB);
        });

        int generatedCount = 0;
        for (int i = 0; i < stepList.size(); i++) {
            JsonNode step = stepList.get(i);
            Long processId = resolveProcessIdFromStep(step);
            int stepSeq = resolveStepSeqFromStep(step, i);

            // 安全网：再次校验工序存在且启用
            BaseProcess process = baseProcessMapper.selectById(processId);
            if (process == null) {
                throw new BaseException("工序不存在：processId=" + processId);
            }
            if (!"0".equals(process.getStatus())) {
                throw new BaseException("工序已禁用：" + process.getProcessName());
            }

            ProWorkProcess record = new ProWorkProcess();
            record.setWorkOrderId(workOrderId);
            record.setProcessId(processId);
            record.setStepSeq(stepSeq);
            // 第一道工序初始化为可开工(READY)，后续为待开始(PENDING)
            record.setStatus(i == 0
                    ? WorkProcessStatus.READY.getCode()
                    : WorkProcessStatus.PENDING.getCode());
            record.setReportQty(java.math.BigDecimal.ZERO);
            record.setVersion(0);
            record.setReworkCount(0);
            record.setCreateTime(java.time.LocalDateTime.now());

            proWorkProcessMapper.insert(record);
            generatedCount++;

            // 写工序状态日志
            statusTransitionLogService.logTransition("WORK_PROCESS", record.getProcessRecordId(),
                    null, record.getStatus(), "auto_generate", "工单下达自动生成");
        }

        log.info("工单下达自动生成工序记录完成：workOrderId={}, 共生成{}道工序", workOrderId, generatedCount);
    }

    /**
     * 从 steps_json 的单个步骤节点解析 processId。
     * 优先直接读取 "processId" 字段，其次通过 "process_code" 或 "processCode" 查找 base_process。
     */
    private Long resolveProcessIdFromStep(JsonNode step) {
        // 方式1：直接包含 processId
        if (step.has("processId")) {
            JsonNode pidNode = step.get("processId");
            if (pidNode.isInt() || pidNode.isLong()) {
                return pidNode.asLong();
            }
            if (pidNode.isTextual()) {
                try {
                    return Long.parseLong(pidNode.asText());
                } catch (NumberFormatException e) {
                    log.warn("processId 为非数字字符串：{}", pidNode.asText());
                }
            }
        }

        // 方式2：通过 process_code（snake_case）或 processCode（camelCase）查找
        String processCode = null;
        if (step.has("process_code")) {
            processCode = step.get("process_code").asText();
        } else if (step.has("processCode")) {
            processCode = step.get("processCode").asText();
        }
        if (processCode != null && !processCode.isBlank()) {
            BaseProcess process = baseProcessMapper.selectOne(
                    new LambdaQueryWrapper<BaseProcess>()
                            .eq(BaseProcess::getProcessCode, processCode));
            if (process != null) {
                return process.getProcessId();
            }
            log.warn("根据 process_code/processCode 未找到工序：{}", processCode);
        }

        return null;
    }

    /**
     * 从 steps_json 的单个步骤节点解析工序顺序号。
     * 优先使用 "seq" 或 "stepSeq" 字段，其次使用数组下标 + 1。
     */
    private int resolveStepSeqFromStep(JsonNode step, int arrayIndex) {
        // 方式1：seq（snake_case 缩写）
        if (step.has("seq")) {
            JsonNode seqNode = step.get("seq");
            int seq = parseSeqNode(seqNode);
            if (seq > 0) return seq;
        }
        // 方式2：stepSeq（camelCase）
        if (step.has("stepSeq")) {
            JsonNode seqNode = step.get("stepSeq");
            int seq = parseSeqNode(seqNode);
            if (seq > 0) return seq;
        }
        return arrayIndex + 1;
    }

    private int parseSeqNode(JsonNode seqNode) {
        if (seqNode.isInt() || seqNode.isLong()) {
            return seqNode.asInt();
        }
        if (seqNode.isTextual()) {
            try {
                return Integer.parseInt(seqNode.asText());
            } catch (NumberFormatException e) {
                log.warn("seq/stepSeq 为非数字字符串：{}", seqNode.asText());
            }
        }
        return 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProWorkOrderVO pauseWorkOrder(Long workOrderId) {
        // [幂等保护]
        java.util.LinkedHashMap<String, Object> idmParams = new java.util.LinkedHashMap<>();
        idmParams.put("workOrderId", workOrderId);
        ProWorkOrderVO cachedVo = checkWorkOrderIdempotency(
                IdempotencyService.ACTION_PAUSE, idmParams, workOrderId);
        if (cachedVo != null) {
            return cachedVo;
        }

        try {
            // 1. 查询工单，获取当前状态
            ProWorkOrder existing = getByIdOrThrow(workOrderId);
            WorkOrderStatus currentStatus = WorkOrderStatus.fromCode(existing.getStatus());

            // 2. 状态转换校验：IN_PROGRESS（生产中）→ PAUSED（已暂停）
            requireWorkOrderTransition(currentStatus, WorkOrderStatus.PAUSED, workOrderId);

            // 3. 乐观锁更新状态
            optimisticUpdateStatus(workOrderId, existing.getVersion(), WorkOrderStatus.PAUSED.getCode());

            // 4. 记录状态转换日志
            statusTransitionLogService.logTransition("WORK_ORDER", workOrderId,
                    currentStatus.getCode(), WorkOrderStatus.PAUSED.getCode(), "pause", null);

            log.info("暂停工单成功：workOrderId={}", workOrderId);
            ProWorkOrderVO vo = getByIdAsVO(workOrderId);
            markWorkOrderIdempotencySuccess(vo);
            return vo;
        } catch (Exception e) {
            markWorkOrderIdempotencyFailure();
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProWorkOrderVO resumeWorkOrder(Long workOrderId) {
        // [幂等保护]
        java.util.LinkedHashMap<String, Object> idmParams = new java.util.LinkedHashMap<>();
        idmParams.put("workOrderId", workOrderId);
        ProWorkOrderVO cachedVo = checkWorkOrderIdempotency(
                IdempotencyService.ACTION_RESUME, idmParams, workOrderId);
        if (cachedVo != null) {
            return cachedVo;
        }

        try {
            // 1. 查询工单，获取当前状态
            ProWorkOrder existing = getByIdOrThrow(workOrderId);
            WorkOrderStatus currentStatus = WorkOrderStatus.fromCode(existing.getStatus());

            // 2. 状态转换校验：PAUSED（已暂停）→ IN_PROGRESS（生产中）
            requireWorkOrderTransition(currentStatus, WorkOrderStatus.IN_PROGRESS, workOrderId);

            // 3. 乐观锁更新状态
            optimisticUpdateStatus(workOrderId, existing.getVersion(), WorkOrderStatus.IN_PROGRESS.getCode());

            // 4. 记录状态转换日志
            statusTransitionLogService.logTransition("WORK_ORDER", workOrderId,
                    currentStatus.getCode(), WorkOrderStatus.IN_PROGRESS.getCode(), "resume", null);

            log.info("恢复工单成功：workOrderId={}", workOrderId);
            ProWorkOrderVO vo = getByIdAsVO(workOrderId);
            markWorkOrderIdempotencySuccess(vo);
            return vo;
        } catch (Exception e) {
            markWorkOrderIdempotencyFailure();
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProWorkOrderVO closeWorkOrder(Long workOrderId) {
        // [幂等保护]
        java.util.LinkedHashMap<String, Object> idmParams = new java.util.LinkedHashMap<>();
        idmParams.put("workOrderId", workOrderId);
        ProWorkOrderVO cachedVo = checkWorkOrderIdempotency(
                IdempotencyService.ACTION_CLOSE, idmParams, workOrderId);
        if (cachedVo != null) {
            return cachedVo;
        }

        try {
            // 1. 查询工单，获取当前状态
            ProWorkOrder existing = getByIdOrThrow(workOrderId);
            WorkOrderStatus currentStatus = WorkOrderStatus.fromCode(existing.getStatus());

            // 2. 状态转换校验：COMPLETED（已完工）→ CLOSED（已关闭）
            requireWorkOrderTransition(currentStatus, WorkOrderStatus.CLOSED, workOrderId);

            // 3. 乐观锁更新状态
            optimisticUpdateStatus(workOrderId, existing.getVersion(), WorkOrderStatus.CLOSED.getCode());

            // 4. 记录状态转换日志
            statusTransitionLogService.logTransition("WORK_ORDER", workOrderId,
                    currentStatus.getCode(), WorkOrderStatus.CLOSED.getCode(), "close", null);

            log.info("关闭工单成功：workOrderId={}", workOrderId);
            ProWorkOrderVO vo = getByIdAsVO(workOrderId);
            markWorkOrderIdempotencySuccess(vo);
            return vo;
        } catch (Exception e) {
            markWorkOrderIdempotencyFailure();
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProWorkOrderVO cancelWorkOrder(Long workOrderId, String remark) {
        // [幂等保护]
        java.util.LinkedHashMap<String, Object> idmParams = new java.util.LinkedHashMap<>();
        idmParams.put("workOrderId", workOrderId);
        idmParams.put("remark", remark);
        ProWorkOrderVO cachedVo = checkWorkOrderIdempotency(
                IdempotencyService.ACTION_CANCEL, idmParams, workOrderId);
        if (cachedVo != null) {
            return cachedVo;
        }

        try {
            // 1. 查询工单，获取当前状态
            ProWorkOrder existing = getByIdOrThrow(workOrderId);
            WorkOrderStatus currentStatus = WorkOrderStatus.fromCode(existing.getStatus());

            // 2. 状态转换校验：允许从 CREATED（已创建）或 RELEASED（已下达）→ CANCELLED（已取消）
            requireWorkOrderTransition(currentStatus, WorkOrderStatus.CANCELLED, workOrderId);

            // 3. 如果工单已下达（RELEASED），级联取消其 PENDING 和 READY 工序
            if (currentStatus == WorkOrderStatus.RELEASED) {
                cancelPendingAndReadyProcesses(workOrderId);
            }

            // 4. 乐观锁更新状态
            optimisticUpdateStatus(workOrderId, existing.getVersion(), WorkOrderStatus.CANCELLED.getCode());

            // 5. 记录状态转换日志
            statusTransitionLogService.logTransition("WORK_ORDER", workOrderId,
                    currentStatus.getCode(), WorkOrderStatus.CANCELLED.getCode(), "cancel", remark);

            log.info("取消工单成功：workOrderId={}, remark={}", workOrderId, remark);
            ProWorkOrderVO vo = getByIdAsVO(workOrderId);
            markWorkOrderIdempotencySuccess(vo);
            return vo;
        } catch (Exception e) {
            markWorkOrderIdempotencyFailure();
            throw e;
        }
    }

    /**
     * 级联取消工单的 PENDING 和 READY 工序。
     * 已开始生产的工序（IN_PROGRESS 及之后）不取消——RELEASED 工单不应当有已开工工序。
     */
    private void cancelPendingAndReadyProcesses(Long workOrderId) {
        java.util.List<ProWorkProcess> processes = proWorkProcessMapper.selectList(
                new LambdaQueryWrapper<ProWorkProcess>()
                        .eq(ProWorkProcess::getWorkOrderId, workOrderId));

        // 先验所有工序是否都可取消
        for (ProWorkProcess process : processes) {
            WorkProcessStatus ps = WorkProcessStatus.fromCode(process.getStatus());
            if (ps != WorkProcessStatus.PENDING && ps != WorkProcessStatus.READY) {
                throw new BaseException("工单已开始生产，存在[" + ps.getLabel() + "]状态的工序，无法取消");
            }
        }

        // 全部可取消，执行级联取消
        for (ProWorkProcess process : processes) {
            ProWorkProcess update = new ProWorkProcess();
            update.setProcessRecordId(process.getProcessRecordId());
            update.setStatus(WorkProcessStatus.CANCELLED.getCode());
            update.setVersion(process.getVersion());
            int rows = proWorkProcessMapper.updateById(update);
            if (rows == 0) {
                throw new StateMachineException(
                        "工序[" + process.getProcessRecordId() + "]并发修改，取消失败");
            }
            statusTransitionLogService.logTransition("WORK_PROCESS",
                    process.getProcessRecordId(),
                    process.getStatus(), WorkProcessStatus.CANCELLED.getCode(),
                    "cancel", "工单取消联动");
        }
    }

    // ==================== 自动流转（由工序 Service 调用） ====================

    /**
     * 任何可执行工序第一次开工时，自动将工单状态从 RELEASED 转为 IN_PROGRESS。
     * 此方法由 ProWorkProcessServiceImpl 在工序 start 成功后调用。
     * <p>
     * 内部有状态守卫：仅当工单当前状态为 RELEASED 时才执行转换，
     * 因此即使多次调用（如多道工序先后开工）也安全幂等。
     */
    @Transactional(rollbackFor = Exception.class)
    public void onFirstProcessStarted(Long workOrderId) {
        ProWorkOrder existing = proWorkOrderMapper.selectById(workOrderId);
        if (existing == null) return;

        WorkOrderStatus currentStatus = WorkOrderStatus.fromCode(existing.getStatus());
        if (currentStatus == WorkOrderStatus.RELEASED) {
            // 原子更新：状态、实际开工时间、version 一次SQL完成
            LambdaUpdateWrapper<ProWorkOrder> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(ProWorkOrder::getWorkOrderId, workOrderId)
                   .eq(ProWorkOrder::getVersion, existing.getVersion())
                   .eq(ProWorkOrder::getStatus, WorkOrderStatus.RELEASED.getCode())
                   .set(ProWorkOrder::getStatus, WorkOrderStatus.IN_PROGRESS.getCode())
                   .set(ProWorkOrder::getActualStartTime, LocalDateTime.now())
                   .setSql("version = version + 1");
            int rows = proWorkOrderMapper.update(null, wrapper);
            if (rows == 0) {
                throw new StateMachineException(MessageConstant.DATA_CONCURRENT_MODIFIED);
            }

            statusTransitionLogService.logTransition("WORK_ORDER", workOrderId,
                    WorkOrderStatus.RELEASED.getCode(), WorkOrderStatus.IN_PROGRESS.getCode(),
                    "start_production", "首道工序开工自动触发");
            log.info("首道工序开工，工单自动进入生产中：workOrderId={}", workOrderId);
            notifyWorkOrder(workOrderId, WsMessageDTO.EVENT_START);
        }
    }

    /**
     * 末道工序完成（或被跳过）时，自动将工单状态转为 COMPLETED。
     * 此方法由 ProWorkProcessServiceImpl 在所有工序均为终端状态后调用。
     * <p>
     * 支持两条路径：
     * <ul>
     *   <li>正常完工：{@code IN_PROGRESS → COMPLETED}</li>
     *   <li>全部工序被跳过（工单从未进入进行中）：{@code RELEASED → COMPLETED}</li>
     * </ul>
     */
    @Transactional(rollbackFor = Exception.class)
    public void onLastProcessCompleted(Long workOrderId) {
        ProWorkOrder existing = proWorkOrderMapper.selectById(workOrderId);
        if (existing == null) return;

        WorkOrderStatus currentStatus = WorkOrderStatus.fromCode(existing.getStatus());
        if (currentStatus == WorkOrderStatus.IN_PROGRESS) {
            // 原子更新：一条 SQL 同时更新 status + actualEndTime + version
            int rows = proWorkOrderMapper.completeWorkOrderAtomically(
                    workOrderId,
                    WorkOrderStatus.COMPLETED.getCode(),
                    WorkOrderStatus.IN_PROGRESS.getCode(),
                    LocalDateTime.now(),
                    existing.getVersion());
            if (rows != 1) {
                throw new StateMachineException(MessageConstant.DATA_CONCURRENT_MODIFIED);
            }

            statusTransitionLogService.logTransition("WORK_ORDER", workOrderId,
                    WorkOrderStatus.IN_PROGRESS.getCode(), WorkOrderStatus.COMPLETED.getCode(),
                    "complete", "末道工序完成自动触发");
            log.info("末道工序完成，工单自动完工：workOrderId={}", workOrderId);
            notifyWorkOrder(workOrderId, WsMessageDTO.EVENT_COMPLETE);
            erpSyncPublisher.publish("WORK_ORDER", workOrderId, "O");
        } else if (currentStatus == WorkOrderStatus.RELEASED) {
            // 全部工序被跳过，工单从未进入进行中，直接从已下达完工
            LambdaUpdateWrapper<ProWorkOrder> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(ProWorkOrder::getWorkOrderId, workOrderId)
                   .eq(ProWorkOrder::getVersion, existing.getVersion())
                   .eq(ProWorkOrder::getStatus, WorkOrderStatus.RELEASED.getCode())
                   .set(ProWorkOrder::getStatus, WorkOrderStatus.COMPLETED.getCode())
                   .set(ProWorkOrder::getActualEndTime, LocalDateTime.now())
                   .setSql("version = version + 1");
            int rows = proWorkOrderMapper.update(null, wrapper);
            if (rows == 0) {
                throw new StateMachineException(MessageConstant.DATA_CONCURRENT_MODIFIED);
            }

            statusTransitionLogService.logTransition("WORK_ORDER", workOrderId,
                    WorkOrderStatus.RELEASED.getCode(), WorkOrderStatus.COMPLETED.getCode(),
                    "complete", "全部工序已跳过，工单自动完工");
            log.info("全部工序已跳过，工单自动完工：workOrderId={}", workOrderId);
            notifyWorkOrder(workOrderId, WsMessageDTO.EVENT_COMPLETE);
            erpSyncPublisher.publish("WORK_ORDER", workOrderId, "O");
        }
    }

    /**
     * 工单状态变更通知：RabbitMQ 启用时发送 MQ（由消费者异步推送 WebSocket），
     * 未启用时降级为同步推送（管理端生产看板/工单列表）。
     */
    private void notifyWorkOrder(Long workOrderId, String eventType) {
        com.bikeassembly.utils.AfterCommitNotification.execute(() -> sendWorkOrderNotification(workOrderId, eventType));
    }

    private void sendWorkOrderNotification(Long workOrderId, String eventType) {
        ProWorkOrder wo = proWorkOrderMapper.selectById(workOrderId);
        if (wo == null) {
            return;
        }
        MessageProducerService producer = messageProducerProvider == null
                ? null : messageProducerProvider.getIfAvailable();
        if (producer != null) {
            try {
                WorkOrderMessageDTO message = WorkOrderMessageDTO.builder()
                        .workOrderId(wo.getWorkOrderId())
                        .orderNo(wo.getOrderNo())
                        .itemId(wo.getItemId())
                        .planQty(wo.getPlanQty())
                        .eventType(eventType)
                        .createTime(wo.getCreateTime())
                        .build();
                producer.sendWorkOrderMessage(message);
                return;
            } catch (Exception e) {
                log.warn("工单消息发送失败（不影响主业务）：workOrderId={}, error={}",
                        wo.getWorkOrderId(), e.getMessage(), e);
            }
        }
        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("workOrderId", wo.getWorkOrderId());
        data.put("workOrderNo", wo.getWorkOrderNo());
        data.put("status", wo.getStatus());
        webSocketNotifier.pushToAdmin(WsMessageDTO.BIZ_WORK_ORDER, eventType, data);
    }

    // ==================== 私有辅助方法 ====================

    private ProWorkOrder getByIdOrThrow(Long workOrderId) {
        ProWorkOrder entity = proWorkOrderMapper.selectById(workOrderId);
        if (entity == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        return entity;
    }

    private ProWorkOrderVO getByIdAsVO(Long workOrderId) {
        return toVO(proWorkOrderMapper.selectById(workOrderId));
    }

    /**
     * [答辩重点] 带乐观锁的状态更新。
     * [并发控制] 使用 MyBatis-Plus @Version 乐观锁机制：
     *   updateById 时 MyBatis-Plus 自动在 WHERE 条件中追加 version = #{version}，
     *   如果并发请求中 version 已被其他事务修改，update 影响行数为 0，
     *   此时抛出 StateMachineException("数据已被其他用户修改，请刷新后重试")。
     * [数据库操作] UPDATE pro_work_order SET status = ?, version = version + 1 WHERE work_order_id = ? AND version = ?
     */
    private void optimisticUpdateStatus(Long workOrderId, Integer expectedVersion, String newStatus) {
        ProWorkOrder update = new ProWorkOrder();
        update.setWorkOrderId(workOrderId);
        update.setStatus(newStatus);
        update.setVersion(expectedVersion);

        int rows = proWorkOrderMapper.updateById(update);
        if (rows == 0) {
            throw new StateMachineException(MessageConstant.DATA_CONCURRENT_MODIFIED);
        }
    }

    /**
     * [状态判断] 状态转换校验：调用 StatusTransitionValidator.canTransition 判断转换是否合法。
     * 不合法时抛出 StateMachineException，前端展示"当前状态不允许此操作"。
     */
    static void requireWorkOrderTransition(WorkOrderStatus from, WorkOrderStatus to, Long entityId) {
        if (!StatusTransitionValidator.canTransition(from, to)) {
            throw new StateMachineException(
                    String.format("工单[%d]不允许从[%s]流转到[%s]", entityId, from.getLabel(), to.getLabel()));
        }
    }

    //校验工单号唯一性，新增 / 编辑时防止重复工单号
    private void checkWorkOrderNoUnique(String workOrderNo, Long excludeId) {
        //MyBatis-Plus 条件构造器，用来拼接查询条件
        LambdaQueryWrapper<ProWorkOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProWorkOrder::getWorkOrderNo, workOrderNo);
        //如果是编辑操作，排除当前id
        if (excludeId != null) {
            wrapper.ne(ProWorkOrder::getWorkOrderId, excludeId);
        }
        if (proWorkOrderMapper.selectCount(wrapper) > 0) {
            throw new BaseException("工单号已存在：" + workOrderNo);
        }
    }

    private ProWorkOrder toEntity(ProWorkOrderDTO dto) {
        ProWorkOrder entity = new ProWorkOrder();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }

    private ProWorkOrderVO toVO(ProWorkOrder entity) {
        ProWorkOrderVO vo = new ProWorkOrderVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    // ==================== 幂等保护辅助方法 ====================

    private ProWorkOrderVO checkWorkOrderIdempotency(String action,
                                                      java.util.Map<String, Object> requestParams,
                                                      Long businessId) {
        String idempotencyKey = BaseContext.getIdempotencyKey();
        if (idempotencyKey == null || BaseContext.getIdempotencyRecordId() != null) {
            return null; // 内部调用，跳过
        }

        Long userId = BaseContext.getCurrentId();
        IdempotentResult result = idempotencyService.tryAcquire(
                idempotencyKey, IdempotencyService.BIZ_TYPE_WORK_ORDER,
                action, requestParams, userId);

        if (result.isDuplicate()) {
            try {
                return objectMapper.readValue(result.getExistingResponse(), ProWorkOrderVO.class);
            } catch (Exception e) {
                log.error("解析工单幂等缓存响应失败：recordId={}", result.getRecordId(), e);
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

    private void markWorkOrderIdempotencySuccess(ProWorkOrderVO vo) {
        Long recordId = BaseContext.getIdempotencyRecordId();
        if (recordId == null) return;
        try {
            String responseJson = objectMapper.writeValueAsString(vo);
            idempotencyService.markSuccess(recordId, vo.getWorkOrderId(), responseJson);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("保存幂等响应失败：recordId={}", recordId, e);
            throw new BaseException("无法保存请求结果，请稍后重试");
        }
    }

    private void markWorkOrderIdempotencyFailure() {
        Long recordId = BaseContext.getIdempotencyRecordId();
        if (recordId != null) idempotencyService.markFailure(recordId, 0, "business_error");
    }

}
