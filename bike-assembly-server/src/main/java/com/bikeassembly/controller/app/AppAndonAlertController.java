package com.bikeassembly.controller.app;

import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.constant.PermissionConstant;
import com.bikeassembly.context.BaseContext;
import com.bikeassembly.dto.AndonAlertDTO;
import com.bikeassembly.dto.AndonAlertEscalateDTO;
import com.bikeassembly.exception.ForbiddenException;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.AndonAlertService;
import com.bikeassembly.service.PermissionService;
import com.bikeassembly.util.UserContextHelper;
import com.bikeassembly.utils.ScopeValidator;
import com.bikeassembly.vo.AndonAlertVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 小程序端安灯告警接口 — 查询 + 上报 + 处理，支持 scope=mine|all + relation=reported|handled|pending。
 *
 * 状态机：0=待处理 → 1=处理中 → 2=已关闭
 */
@RestController
@RequestMapping("/app/andon-alert")
@Slf4j
public class AppAndonAlertController {

    @Autowired
    private AndonAlertService andonAlertService;
    @Autowired
    private PermissionService permissionService;
    @Autowired
    private UserContextHelper userContextHelper;

    /**
     * GET /app/andon-alert/list
     * 获取告警列表。
     *
     * scope:
     *   mine - 本人相关（默认，由 relation 决定具体含义）
     *   all  - 全部数据（需 view-all 权限 + ALL data_scope）
     *
     * relation:
     *   reported - reporter_user_id = 当前用户 (mine 默认)
     *   handled  - handler_user_id = 当前用户
     *   pending  - handler_user_id IS NULL 且 status='0'（需 view-pending 权限）
     */
    @GetMapping("/list")
    @RequirePermission(PermissionConstant.APP_ANDON_VIEW)
    public Result<List<AndonAlertVO>> list(@RequestParam(defaultValue = "mine") String scope,
                                            @RequestParam(required = false) String relation,
                                            @RequestParam(required = false) Long workOrderId) {
        // Controller 层严格校验 scope 合法值，拒绝未知值、空字符串、空格、大小写变体
        scope = ScopeValidator.requireValid(scope, ScopeValidator.MINE_OR_ALL);
        Long userId = BaseContext.getCurrentId();

        if ("all".equals(scope)) {
            if (!permissionService.hasPermission(userId, PermissionConstant.APP_ANDON_VIEW_ALL)) {
                throw new ForbiddenException(MessageConstant.PERMISSION_DENIED);
            }
        }

        // pending 需要独立权限，不属于 mine 数据（relation 参数，不是 scope）
        if ("pending".equals(relation)) {
            if (!permissionService.hasPermission(userId, PermissionConstant.APP_ANDON_VIEW_PENDING)) {
                throw new ForbiddenException(MessageConstant.PERMISSION_DENIED);
            }
        }

        PageResult<AndonAlertVO> pageResult = andonAlertService.listAndonAlerts(1L, 9999L, scope, relation);
        List<AndonAlertVO> list = pageResult.getRecords();
        if (workOrderId != null) {
            list = list.stream().filter(v -> workOrderId.equals(v.getWorkOrderId())).collect(Collectors.toList());
        }
        return Result.success(list);
    }

    /**
     * GET /app/andon-alert/{id}
     * 获取单条告警详情。
     * 归属校验：上报人、当前处理人 或 拥有 view-all 权限的用户可查看。
     */
    @GetMapping("/{id}")
    @RequirePermission(PermissionConstant.APP_ANDON_VIEW)
    public Result<AndonAlertVO> getById(@PathVariable Long id) {
        AndonAlertVO vo = andonAlertService.getAndonAlertById(id);
        // App 归属校验：上报人、处理人 或 app:andon:view-all
        Long currentUserId = BaseContext.getCurrentId();
        boolean isReporter = vo.getReporterUserId() != null && vo.getReporterUserId().equals(currentUserId);
        boolean isHandler = vo.getHandlerUserId() != null && vo.getHandlerUserId().equals(currentUserId);
        boolean hasViewAll = permissionService.hasPermission(currentUserId, PermissionConstant.APP_ANDON_VIEW_ALL);
        if (!isReporter && !isHandler && !hasViewAll) {
            throw new ForbiddenException(MessageConstant.PERMISSION_DENIED);
        }
        return Result.success(vo);
    }

    /**
     * POST /app/andon-alert
     * 上报告警。上报人由后端自动填充。
     */
    @PostMapping
    @RequirePermission(PermissionConstant.APP_ANDON_CREATE)
    public Result<AndonAlertVO> create(@Validated @RequestBody AndonAlertDTO dto) {
        // 禁止前端传入 user_id 字段
        dto.setReporterUserId(null);
        dto.setHandlerUserId(null);

        if (dto.getAlertNo() == null || dto.getAlertNo().isBlank()) {
            dto.setAlertNo(generateAlertNo());
        }
        if (dto.getCreateTime() == null) {
            dto.setCreateTime(LocalDateTime.now());
        }
        dto.setReporterUserId(userContextHelper.getCurrentUserId());
        log.info("小程序上报安灯告警：alertType={}, alertLevel={}, workOrderId={}, equipmentId={}",
                dto.getAlertType(), dto.getAlertLevel(), dto.getWorkOrderId(), dto.getEquipmentId());
        AndonAlertVO vo = andonAlertService.saveAndonAlert(dto);
        return Result.success(vo);
    }

    /**
     * PUT /app/andon-alert/{id}/accept
     * 受理告警：将待处理状态变为处理中。
     * 处理人由后端自动填充为当前用户。
     */
    @PutMapping("/{id}/accept")
    @RequirePermission(PermissionConstant.APP_ANDON_HANDLE)
    public Result<AndonAlertVO> accept(@PathVariable Long id) {
        log.info("小程序受理告警：alertId={}, userId={}", id, BaseContext.getCurrentId());
        AndonAlertVO vo = andonAlertService.acceptAndonAlert(id);
        return Result.success(vo);
    }

    /**
     * PUT /app/andon-alert/{id}/close
     * 关闭告警：将处理中状态变为已关闭。
     */
    @PutMapping("/{id}/close")
    @RequirePermission(PermissionConstant.APP_ANDON_CLOSE)
    public Result<AndonAlertVO> close(@PathVariable Long id) {
        log.info("小程序关闭告警：alertId={}, userId={}", id, BaseContext.getCurrentId());
        AndonAlertVO vo = andonAlertService.closeAndonAlert(id);
        return Result.success(vo);
    }

    /**
     * PUT /app/andon-alert/{id}/escalate
     * 升级告警：待处理(0)/处理中(1) 可升级，上报人/处理人/ALL 数据范围用户可操作。
     */
    @PutMapping("/{id}/escalate")
    @RequirePermission(PermissionConstant.APP_ANDON_HANDLE)
    public Result<AndonAlertVO> escalate(@PathVariable Long id,
                                         @RequestBody(required = false) AndonAlertEscalateDTO dto) {
        log.info("小程序升级告警：alertId={}, userId={}", id, BaseContext.getCurrentId());
        AndonAlertVO vo = andonAlertService.escalateAndonAlert(id, dto);
        return Result.success(vo);
    }

    private String generateAlertNo() {
        return "ALERT" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }
}
