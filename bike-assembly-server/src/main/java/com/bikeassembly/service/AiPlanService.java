package com.bikeassembly.service;

import com.bikeassembly.dto.AiQueryDTO;
import com.bikeassembly.dto.DashboardScreenQueryDTO;
import com.bikeassembly.context.BaseContext;
import com.bikeassembly.entity.AiPlanSuggestion;
import com.bikeassembly.mapper.AiPlanSuggestionMapper;
import com.bikeassembly.mapper.AiMesDataMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bikeassembly.vo.AiPlanSuggestionVO;
import com.bikeassembly.vo.DashboardScreenVO;
import com.bikeassembly.ws.WebSocketNotifier;
import com.bikeassembly.ws.WsMessageDTO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Map;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiPlanService {
    private final AiQueryService aiQueryService;
    private final DashboardScreenService dashboardScreenService;
    private final AiMesDataMapper mesDataMapper;
    private final DataScopeService dataScopeService;
    private final AiRequestAuditService auditService;
    private final AiPlanSuggestionMapper mapper;
    private final ObjectMapper objectMapper;
    private final WebSocketNotifier webSocketNotifier;
    private final IdempotencyService idempotencyService;

    public AiPlanSuggestionVO create(AiQueryDTO request) {
        Long operatorId = requireOperator();
        var result = aiQueryService.ask(request);
        PlanningContext planning = buildPlanningContext(request);
        AiPlanSuggestion entity = new AiPlanSuggestion();
        entity.setPlanUuid(UUID.randomUUID().toString());
        entity.setOperatorId(operatorId);
        entity.setQuestion(request.getQuestion());
        entity.setSummary(result.getAnswer());
        entity.setAffectedOrdersJson(writeJson(planning.affectedOrders()));
        entity.setPriorityOrderJson(writeJson(planning.priorityOrder()));
        entity.setConstraintsJson(writeJson(planning.constraints()));
        entity.setResourceConstraintsJson(writeJson(planning.resourceConstraints()));
        entity.setMaterialConstraintsJson(writeJson(planning.materialConstraints()));
        entity.setRiskLevel(planning.riskLevel());
        entity.setRiskBasis(planning.riskBasis());
        entity.setDataTimeRange(result.getDataTimeRange());
        entity.setSourcesJson(writeJson(result.getSources()));
        entity.setInputSummary(planning.inputSummary());
        entity.setModel(result.getModel());
        entity.setRequestId(result.getRequestId());
        entity.setSessionId(request.getSessionId());
        entity.setStatus("PENDING_CONFIRMATION");
        entity.setCreatedAt(LocalDateTime.now());
        mapper.insert(entity);
        webSocketNotifier.pushToAdminUser(operatorId, WsMessageDTO.BIZ_AI,
                WsMessageDTO.EVENT_AI_PLAN_READY,
                Map.of("planId", entity.getPlanUuid(),
                        "sessionId", request.getSessionId() == null ? "" : request.getSessionId(),
                        "status", entity.getStatus()));
        auditService.record(result.getRequestId(), request.getSessionId(), operatorId, "PLAN",
                request.getQuestion(), result.getDataScope(), result.getDataTimeRange(),
                result.getModel(), result.getSources(), planning.inputSummary(),
                "SUCCESS", result.isFallback(), 0, null);
        return toVO(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public AiPlanSuggestionVO confirm(String planId) {
        Long operatorId = requireOperator();
        String key = BaseContext.getIdempotencyKey();
        IdempotentResult idempotency = idempotencyService.tryAcquire(
                key, IdempotencyService.BIZ_TYPE_AI_PLAN,
                IdempotencyService.ACTION_AI_PLAN_CONFIRM,
                Map.of("planId", planId), operatorId);
        if (idempotency.isDuplicate()) {
            AiPlanSuggestionVO existing = get(planId);
            if (existing == null) throw new IllegalStateException("排产建议不存在");
            return existing;
        }
        if (idempotency.isProcessing()) {
            throw new IllegalStateException("确认请求处理中，请稍后重试");
        }
        idempotencyService.lockExecution(idempotency.getRecordId());
        AiPlanSuggestion plan = mapper.selectByUuidAndOperator(planId, operatorId);
        if (plan == null) throw new IllegalArgumentException("排产建议不存在");
        if (!"PENDING_CONFIRMATION".equals(plan.getStatus())) {
            throw new IllegalStateException("排产建议已处理");
        }
        AiPlanSuggestion updated = new AiPlanSuggestion();
        updated.setStatus("CONFIRMED_NO_AUTO_EXECUTION");
        updated.setConfirmedAt(LocalDateTime.now());
        updated.setConfirmedBy(operatorId);
        int rows = mapper.update(updated, new LambdaUpdateWrapper<AiPlanSuggestion>()
                .eq(AiPlanSuggestion::getPlanUuid, planId)
                .eq(AiPlanSuggestion::getOperatorId, operatorId)
                .eq(AiPlanSuggestion::getStatus, "PENDING_CONFIRMATION"));
        if (rows != 1) throw new IllegalStateException("排产建议状态已变更");
        plan.setStatus(updated.getStatus());
        plan.setConfirmedAt(updated.getConfirmedAt());
        plan.setConfirmedBy(updated.getConfirmedBy());
        AiPlanSuggestionVO result = toVO(plan);
        try {
            idempotencyService.markSuccess(idempotency.getRecordId(), plan.getPlanId(),
                    objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            throw new IllegalStateException("保存幂等响应失败", e);
        }
        return result;
    }

    public AiPlanSuggestionVO get(String planId) {
        AiPlanSuggestion plan = mapper.selectByUuidAndOperator(planId, requireOperator());
        return plan == null ? null : toVO(plan);
    }

    private Long requireOperator() {
        Long id = BaseContext.getCurrentId();
        if (id == null) throw new IllegalStateException("未获取到当前用户");
        return id;
    }

    private AiPlanSuggestionVO toVO(AiPlanSuggestion entity) {
        try {
            return AiPlanSuggestionVO.builder()
                    .planId(entity.getPlanUuid())
                    .status(entity.getStatus())
                    .summary(entity.getSummary())
                    .affectedOrders(readJson(entity.getAffectedOrdersJson()))
                    .priorityOrder(readJson(entity.getPriorityOrderJson()))
                    .constraints(readJson(entity.getConstraintsJson()))
                    .resourceConstraints(readJson(entity.getResourceConstraintsJson()))
                    .materialConstraints(readJson(entity.getMaterialConstraintsJson()))
                    .riskLevel(entity.getRiskLevel())
                    .riskBasis(entity.getRiskBasis())
                    .dataTimeRange(entity.getDataTimeRange())
                    .sources(readJson(entity.getSourcesJson()))
                    .inputSummary(entity.getInputSummary())
                    .model(entity.getModel())
                    .requestId(entity.getRequestId())
                    .sessionId(entity.getSessionId())
                    .createdAt(entity.getCreatedAt())
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("排产建议数据损坏", e);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> readJson(String value) {
        try {
            return objectMapper.readValue(value == null ? "[]" : value,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    private PlanningContext buildPlanningContext(AiQueryDTO request) {
        int days = request.getDays() == null ? 7 : Math.min(Math.max(request.getDays(), 1), 30);
        DashboardScreenQueryDTO query = new DashboardScreenQueryDTO();
        query.setDays(days);
        DashboardScreenVO screen = dashboardScreenService.screen(query);
        DataScopeService.Scope scope = dataScopeService.current();
        Long userId = "SELF".equals(scope.name()) || "DENIED".equals(scope.name())
                ? scope.userId() == null ? -1L : scope.userId() : null;
        List<Map<String, Object>> inventory = mesDataMapper.inventorySummary(userId, scope.unitIds());

        List<DashboardScreenVO.WorkOrder> workOrders = screen == null || screen.getWorkOrders() == null
                ? List.of() : screen.getWorkOrders();
        List<DashboardScreenVO.WorkOrder> ordered = workOrders.stream()
                .sorted(Comparator.comparing(DashboardScreenVO.WorkOrder::isOverdue).reversed()
                        .thenComparing(DashboardScreenVO.WorkOrder::getPlanEndTime,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(DashboardScreenVO.WorkOrder::getWorkOrderId,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(12)
                .toList();
        List<String> affectedOrders = ordered.stream()
                .map(this::formatWorkOrder)
                .toList();
        List<String> priorityOrder = ordered.stream()
                .map(order -> formatWorkOrder(order) + (order.isOverdue() ? "（优先处理：已逾期）" : ""))
                .toList();

        List<String> resourceConstraints = new java.util.ArrayList<>();
        if (screen != null && screen.getEquipment() != null && screen.getEquipment().getFault() > 0) {
            resourceConstraints.add("当前可见范围有 " + screen.getEquipment().getFault() + " 台设备处于故障状态");
        }
        long abnormalProcesses = screen == null || screen.getProcesses() == null ? 0
                : screen.getProcesses().stream().mapToLong(DashboardScreenVO.ProcessLoad::getAbnormal).sum();
        if (abnormalProcesses > 0) {
            resourceConstraints.add("当前工序负荷中有 " + abnormalProcesses + " 条异常记录");
        }
        if (resourceConstraints.isEmpty()) {
            resourceConstraints.add("未从当前看板检索到设备故障或工序异常；执行前仍需人工确认设备/工位可用性");
        }

        List<String> materialConstraints = new java.util.ArrayList<>();
        if (!"ALL".equals(scope.name())) {
            materialConstraints.add("当前数据范围为 " + scope.name()
                    + "；库存没有人员归属，未纳入本次建议");
        } else if (inventory.isEmpty()) {
            materialConstraints.add("未检索到库存快照，物料可用量需要人工确认");
        } else {
            inventory.stream().limit(8).forEach(row -> {
                BigDecimal available = decimal(row, "quantity").subtract(decimal(row, "lockedQuantity"));
                if (available.signum() <= 0) {
                    materialConstraints.add(text(row, "itemName") + " 可用量不足（"
                            + available.stripTrailingZeros().toPlainString() + " " + text(row, "unit") + "）");
                }
            });
            if (materialConstraints.isEmpty()) {
                materialConstraints.add("当前库存快照未发现可用量小于等于零的物料；仍需按 BOM 和领料计划复核");
            }
        }

        List<String> riskReasons = new java.util.ArrayList<>();
        long overdue = ordered.stream().filter(DashboardScreenVO.WorkOrder::isOverdue).count();
        if (overdue > 0) riskReasons.add("有 " + overdue + " 个逾期工单");
        if (screen != null && screen.getSummary() != null && screen.getSummary().getOpenAlerts() > 0) {
            riskReasons.add("存在 " + screen.getSummary().getOpenAlerts() + " 个未关闭安灯告警");
        }
        if (screen != null && screen.getEquipment() != null && screen.getEquipment().getFault() > 0) {
            riskReasons.add("存在设备故障约束");
        }
        if (screen != null && screen.getSummary() != null && screen.getSummary().getPendingInspections() > 0) {
            riskReasons.add("存在待检质量任务");
        }
        String riskLevel = (overdue > 0
                || (screen != null && screen.getEquipment() != null && screen.getEquipment().getFault() > 0)
                || (screen != null && screen.getSummary() != null && screen.getSummary().getOpenAlerts() > 0))
                ? "高" : riskReasons.isEmpty() ? "低" : "中";
        String riskBasis = riskReasons.isEmpty()
                ? "当前可见数据未发现明显逾期、设备故障或未关闭告警；建议仍需人工复核。"
                : String.join("；", riskReasons) + "。AI 只生成草稿，不会自动修改工单、库存或设备。";
        List<String> constraints = new java.util.ArrayList<>();
        constraints.addAll(resourceConstraints);
        constraints.addAll(materialConstraints);
        constraints.add("只使用当前用户数据范围 " + scope.name() + " 内的只读数据");
        constraints.add("确认后仅留存建议草稿，不自动执行排产");

        String inputSummary = "问题：" + shorten(request.getQuestion(), 900)
                + "；可见工单：" + affectedOrders.size()
                + "；数据范围：" + scope.name()
                + "；统计天数：" + days;
        return new PlanningContext(affectedOrders, priorityOrder, constraints,
                resourceConstraints, materialConstraints, riskLevel, riskBasis, inputSummary);
    }

    private String formatWorkOrder(DashboardScreenVO.WorkOrder order) {
        String number = text(order.getWorkOrderNo(), order.getWorkOrderId() == null
                ? "未编号工单" : "工单#" + order.getWorkOrderId());
        String item = text(order.getItemName(), "未命名产品");
        String progress = order.getTotalSteps() <= 0 ? "工序进度未知"
                : order.getCompletedSteps() + "/" + order.getTotalSteps() + " 道工序完成";
        return number + "（" + item + "，" + progress + "）";
    }

    private String text(Object value, String fallback) {
        if (value == null || String.valueOf(value).isBlank()) return fallback;
        return String.valueOf(value);
    }

    private String text(Map<String, Object> row, String key) {
        return text(row == null ? null : row.get(key), "");
    }

    private BigDecimal decimal(Map<String, Object> row, String key) {
        Object value = row == null ? null : row.get(key);
        try {
            return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private String shorten(String value, int max) {
        if (value == null) return "";
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    private record PlanningContext(List<String> affectedOrders,
                                   List<String> priorityOrder,
                                   List<String> constraints,
                                   List<String> resourceConstraints,
                                   List<String> materialConstraints,
                                   String riskLevel,
                                   String riskBasis,
                                   String inputSummary) {
    }
}
