package com.bikeassembly.service.impl;

import com.bikeassembly.constant.PermissionConstant;
import com.bikeassembly.constant.ScanCodeConstant;
import com.bikeassembly.context.BaseContext;
import com.bikeassembly.entity.ProWorkOrder;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.exception.ForbiddenException;
import com.bikeassembly.exception.UnauthorizedException;
import com.bikeassembly.mapper.ProWorkOrderMapper;
import com.bikeassembly.service.PermissionService;
import com.bikeassembly.service.QmsInspectionService;
import com.bikeassembly.service.WorkOrderScanService;
import com.bikeassembly.vo.ScanResolveVO;
import com.bikeassembly.vo.WorkOrderQrCodeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class WorkOrderScanServiceImpl implements WorkOrderScanService {
    private static final Pattern NUMBER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Pattern ID = Pattern.compile("[1-9][0-9]{0,15}");
    private final ProWorkOrderMapper orders;
    private final QmsInspectionService inspections;
    private final PermissionService permissions;

    @Override
    public WorkOrderQrCodeVO getQrCode(Long workOrderId) {
        Long userId = currentUser();
        if (!allowed(userId, PermissionConstant.ADMIN_WORK_ORDER_VIEW)) throw denied();
        requireSafeId(workOrderId);
        ProWorkOrder order = requireOrder(orders.selectById(workOrderId));
        WorkOrderQrCodeVO result = new WorkOrderQrCodeVO();
        result.setWorkOrderId(order.getWorkOrderId());
        result.setWorkOrderNo(order.getWorkOrderNo());
        result.setContent(ScanCodeConstant.PREFIX + order.getWorkOrderId());
        return result;
    }

    @Override
    public ScanResolveVO resolve(String content) {
        Long userId = currentUser();
        boolean orderView = allowed(userId, PermissionConstant.APP_WORK_ORDER_VIEW);
        boolean processView = allowed(userId, PermissionConstant.APP_WORK_PROCESS_VIEW);
        boolean inspectionView = allowed(userId, PermissionConstant.APP_INSPECTION_VIEW);
        if (!orderView && !processView && !inspectionView) throw denied();
        ProWorkOrder order = parseOrder(content);
        boolean owner = userId.equals(order.getAssigneeUserId());
        List<String> actions = new ArrayList<>();
        if (orderView && (owner || allowed(userId, PermissionConstant.APP_WORK_ORDER_VIEW_ALL))) {
            actions.add(ScanCodeConstant.WORK_ORDER_DETAIL);
        }
        if (processView && (owner || allowed(userId, PermissionConstant.APP_WORK_PROCESS_VIEW_ALL))) {
            actions.add(ScanCodeConstant.WORK_PROCESS);
        }
        long pending = inspectionView ? inspections.countAccessiblePending(order.getWorkOrderId()) : 0;
        if (pending > 0) actions.add(ScanCodeConstant.INSPECTION_PENDING);
        if (actions.isEmpty()) {
            if (inspectionView && (owner || allowed(userId, PermissionConstant.APP_INSPECTION_VIEW_ALL)
                    || allowed(userId, PermissionConstant.APP_INSPECTION_VIEW_PENDING))) {
                throw new BaseException(ScanCodeConstant.NO_PENDING_INSPECTION, "当前工单暂无可访问的待质检任务");
            }
            throw denied();
        }
        ScanResolveVO result = new ScanResolveVO();
        result.setBusinessType(ScanCodeConstant.WORK_ORDER);
        result.setWorkOrderId(order.getWorkOrderId());
        result.setWorkOrderNo(order.getWorkOrderNo());
        result.setActions(List.copyOf(actions));
        result.setPendingInspectionCount(pending);
        return result;
    }

    private ProWorkOrder parseOrder(String content) {
        if (content == null || content.isEmpty() || content.length() > 256
                || content.chars().anyMatch(Character::isISOControl)) throw invalid();
        if (content.startsWith("BIKE-MES:")) {
            String[] parts = content.split(":", -1);
            if (parts.length != 4) throw invalid();
            if (!"1".equals(parts[1]) || !ScanCodeConstant.WORK_ORDER.equals(parts[2])) {
                throw new BaseException(ScanCodeConstant.UNSUPPORTED_CODE, "暂不支持此码类型或版本，请扫描一期工单码");
            }
            if (!ID.matcher(parts[3]).matches()) throw invalid();
            long id;
            try { id = Long.parseLong(parts[3]); }
            catch (NumberFormatException ex) { throw invalid(); }
            requireSafeId(id);
            return requireOrder(orders.selectById(id));
        }
        if (!NUMBER.matcher(content).matches()) throw invalid();
        return requireOrder(orders.selectByWorkOrderNo(content));
    }

    private ProWorkOrder requireOrder(ProWorkOrder order) {
        if (order == null) throw new BaseException(ScanCodeConstant.ORDER_NOT_FOUND, "工单不存在或已删除");
        requireSafeId(order.getWorkOrderId());
        return order;
    }
    private void requireSafeId(Long id) {
        if (id == null || id <= 0 || id > ScanCodeConstant.MAX_SAFE_ID) throw invalid();
    }
    private boolean allowed(Long userId, String permission) { return permissions.hasPermission(userId, permission); }
    private Long currentUser() {
        Long id = BaseContext.getCurrentId();
        if (id == null) throw new UnauthorizedException("请先登录");
        return id;
    }
    private ForbiddenException denied() { return new ForbiddenException("您无权访问该工单的任务"); }
    private BaseException invalid() {
        return new BaseException(ScanCodeConstant.INVALID_CODE, "码内容无效，请扫描工单二维码或工单编号条形码");
    }
}
