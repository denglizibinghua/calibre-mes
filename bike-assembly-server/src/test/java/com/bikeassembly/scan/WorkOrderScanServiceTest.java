package com.bikeassembly.scan;

import com.bikeassembly.constant.PermissionConstant;
import com.bikeassembly.context.BaseContext;
import com.bikeassembly.entity.ProWorkOrder;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.exception.ForbiddenException;
import com.bikeassembly.exception.UnauthorizedException;
import com.bikeassembly.mapper.ProWorkOrderMapper;
import com.bikeassembly.service.PermissionService;
import com.bikeassembly.service.QmsInspectionService;
import com.bikeassembly.service.impl.WorkOrderScanServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkOrderScanServiceTest {
    @Mock ProWorkOrderMapper orders;
    @Mock QmsInspectionService inspections;
    @Mock PermissionService permissions;
    WorkOrderScanServiceImpl service;
    final Set<String> granted = new HashSet<>();
    ProWorkOrder order;

    @BeforeEach void setup() {
        service = new WorkOrderScanServiceImpl(orders, inspections, permissions);
        BaseContext.setCurrentId(10L);
        lenient().when(permissions.hasPermission(eq(10L), anyString()))
                .thenAnswer(call -> granted.contains(call.getArgument(1)));
        order = new ProWorkOrder();
        order.setWorkOrderId(42L);
        order.setWorkOrderNo("MO-20261027-0042");
        order.setAssigneeUserId(10L);
    }
    @AfterEach void cleanup() { BaseContext.removeCurrentId(); }

    @Test void operatorCanLocateOwnOrderWithoutAnyWrites() {
        granted.add(PermissionConstant.APP_WORK_ORDER_VIEW);
        granted.add(PermissionConstant.APP_WORK_PROCESS_VIEW);
        when(orders.selectById(42L)).thenReturn(order);
        var result = service.resolve("BIKE-MES:1:WORK_ORDER:42");
        assertEquals(42L, result.getWorkOrderId());
        assertEquals(order.getWorkOrderNo(), result.getWorkOrderNo());
        assertEquals(java.util.List.of("WORK_ORDER_DETAIL", "WORK_PROCESS"), result.getActions());
        verify(orders).selectById(42L);
        verifyNoMoreInteractions(orders);
        verifyNoInteractions(inspections);
    }
    @Test void barcodeUsesExactNumberAndNeverTreatsBareNumberAsId() {
        granted.add(PermissionConstant.APP_WORK_ORDER_VIEW);
        when(orders.selectByWorkOrderNo("42")).thenReturn(order);
        assertEquals(42L, service.resolve("42").getWorkOrderId());
        verify(orders, never()).selectById(anyLong());
    }
    @Test void inspectorDoesNotNeedProductionOrderPermission() {
        granted.add(PermissionConstant.APP_INSPECTION_VIEW);
        granted.add(PermissionConstant.APP_INSPECTION_VIEW_PENDING);
        order.setAssigneeUserId(20L);
        when(orders.selectById(42L)).thenReturn(order);
        when(inspections.countAccessiblePending(42L)).thenReturn(2L);
        var result = service.resolve("BIKE-MES:1:WORK_ORDER:42");
        assertEquals(java.util.List.of("INSPECTION_PENDING"), result.getActions());
        assertEquals(2L, result.getPendingInspectionCount());
        verify(inspections).countAccessiblePending(42L);
        verifyNoMoreInteractions(inspections);
    }
    @Test void combinedPermissionsProduceAllApprovedActions() {
        granted.addAll(Set.of(PermissionConstant.APP_WORK_ORDER_VIEW, PermissionConstant.APP_WORK_PROCESS_VIEW,
                PermissionConstant.APP_INSPECTION_VIEW));
        when(orders.selectById(42L)).thenReturn(order);
        when(inspections.countAccessiblePending(42L)).thenReturn(1L);
        assertEquals(3, service.resolve("BIKE-MES:1:WORK_ORDER:42").getActions().size());
    }
    @Test void processOnlyPermissionHasIndependentEntry() {
        granted.add(PermissionConstant.APP_WORK_PROCESS_VIEW);
        when(orders.selectById(42L)).thenReturn(order);
        assertEquals(java.util.List.of("WORK_PROCESS"), service.resolve("BIKE-MES:1:WORK_ORDER:42").getActions());
    }
    @Test void otherAssigneeIsDenied() {
        granted.add(PermissionConstant.APP_WORK_ORDER_VIEW);
        order.setAssigneeUserId(20L);
        when(orders.selectById(42L)).thenReturn(order);
        assertThrows(ForbiddenException.class, () -> service.resolve("BIKE-MES:1:WORK_ORDER:42"));
    }
    @Test void viewAllStillRequiresBasePermission() {
        granted.add(PermissionConstant.APP_WORK_ORDER_VIEW_ALL);
        assertThrows(ForbiddenException.class, () -> service.resolve("BIKE-MES:1:WORK_ORDER:42"));
        verifyNoInteractions(orders);
    }
    @Test void viewAllCanLocateOtherAssignee() {
        granted.addAll(Set.of(PermissionConstant.APP_WORK_ORDER_VIEW, PermissionConstant.APP_WORK_ORDER_VIEW_ALL));
        order.setAssigneeUserId(20L);
        when(orders.selectById(42L)).thenReturn(order);
        assertEquals(java.util.List.of("WORK_ORDER_DETAIL"), service.resolve("BIKE-MES:1:WORK_ORDER:42").getActions());
    }
    @Test void inspectorWithNoPendingGetsSpecificBusinessMessage() {
        granted.addAll(Set.of(PermissionConstant.APP_INSPECTION_VIEW, PermissionConstant.APP_INSPECTION_VIEW_PENDING));
        order.setAssigneeUserId(20L);
        when(orders.selectById(42L)).thenReturn(order);
        when(inspections.countAccessiblePending(42L)).thenReturn(0L);
        assertEquals(46004, assertThrows(BaseException.class,
                () -> service.resolve("BIKE-MES:1:WORK_ORDER:42")).getCode());
    }
    @Test void noPermissionIsDeniedBeforeReadingOrder() {
        assertThrows(ForbiddenException.class, () -> service.resolve("BIKE-MES:1:WORK_ORDER:42"));
        verifyNoInteractions(orders, inspections);
    }
    @Test void notLoggedInIsUnauthorized() {
        BaseContext.removeCurrentId();
        assertThrows(UnauthorizedException.class, () -> service.resolve("BIKE-MES:1:WORK_ORDER:42"));
    }
    @Test void missingOrderHasSpecificCode() {
        granted.add(PermissionConstant.APP_WORK_ORDER_VIEW);
        assertEquals(46003, assertThrows(BaseException.class,
                () -> service.resolve("BIKE-MES:1:WORK_ORDER:999999")).getCode());
    }
    @ParameterizedTest
    @ValueSource(strings={"", " ", "https://evil.test/pages/work-order/detail?id=42", "/pages/home/index",
            "{}", "BIKE-MES:1:WORK_ORDER:0", "BIKE-MES:1:WORK_ORDER:-42", "BIKE-MES:1:WORK_ORDER:01",
            "BIKE-MES:1:WORK_ORDER:42:extra", "BIKE-MES:1:WORK_ORDER:9223372036854775808",
            "BIKE-MES:1:WORK_ORDER:9007199254740992", "BIKE-MES:1:WORK_ORDER:42\n"})
    void invalidCodesNeverQueryDatabase(String code) {
        granted.add(PermissionConstant.APP_WORK_ORDER_VIEW);
        assertEquals(46001, assertThrows(BaseException.class, () -> service.resolve(code)).getCode());
        verifyNoInteractions(orders, inspections);
    }
    @ParameterizedTest @ValueSource(strings={"BIKE-MES:2:WORK_ORDER:42", "BIKE-MES:1:EQUIPMENT:42", "BIKE-MES:1:ITEM:42"})
    void unsupportedTypeAndVersionAreExplicit(String code) {
        granted.add(PermissionConstant.APP_WORK_ORDER_VIEW);
        assertEquals(46002, assertThrows(BaseException.class, () -> service.resolve(code)).getCode());
    }
    @Test void oversizedCodeIsInvalid() {
        granted.add(PermissionConstant.APP_WORK_ORDER_VIEW);
        assertEquals(46001, assertThrows(BaseException.class, () -> service.resolve("A".repeat(257))).getCode());
    }
    @Test void adminMetadataRequiresRealOrderAndReadPermission() {
        granted.add(PermissionConstant.ADMIN_WORK_ORDER_VIEW);
        when(orders.selectById(42L)).thenReturn(order);
        assertEquals("BIKE-MES:1:WORK_ORDER:42", service.getQrCode(42L).getContent());
        granted.clear();
        assertThrows(ForbiddenException.class, () -> service.getQrCode(42L));
    }
}
