package com.bikeassembly.review;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bikeassembly.context.BaseContext;
import com.bikeassembly.entity.ProWorkOrder;
import com.bikeassembly.entity.QmsInspection;
import com.bikeassembly.mapper.ProWorkOrderMapper;
import com.bikeassembly.mapper.QmsInspectionMapper;
import com.bikeassembly.service.impl.QmsInspectionServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InspectionWorkOrderNumberTest {
    @Test
    void onlyLooksUpWorkOrdersFromVisibleInspectionTasks() {
        QmsInspectionMapper inspections = mock(QmsInspectionMapper.class);
        ProWorkOrderMapper orders = mock(ProWorkOrderMapper.class);
        QmsInspectionServiceImpl service = new QmsInspectionServiceImpl();
        ReflectionTestUtils.setField(service, "qmsInspectionMapper", inspections);
        ReflectionTestUtils.setField(service, "proWorkOrderMapper", orders);
        ReflectionTestUtils.setField(service, "permissionService", mock(com.bikeassembly.service.PermissionService.class));

        QmsInspection first = new QmsInspection();
        first.setInspectionId(1L);
        first.setWorkOrderId(10L);
        QmsInspection second = new QmsInspection();
        second.setInspectionId(2L);
        second.setWorkOrderId(10L);
        Page<QmsInspection> page = new Page<>(1, 10);
        page.setRecords(List.of(first, second));
        page.setTotal(2);
        when(inspections.selectPageByCondition(any(), isNull(), isNull(), isNull(), eq(99L), isNull(), eq(false), isNull())).thenReturn(page);
        ProWorkOrder order = new ProWorkOrder();
        order.setWorkOrderId(10L);
        order.setWorkOrderNo("WO-0010");
        when(orders.selectBatchIds(List.of(10L))).thenReturn(List.of(order));

        BaseContext.setCurrentId(99L);
        try {
            var result = service.listQmsInspections(1L, 10L, null, null, null, "mine", null);
            assertEquals(List.of("WO-0010", "WO-0010"), result.getRecords().stream().map(item -> item.getWorkOrderNo()).toList());
            verify(orders, times(1)).selectBatchIds(List.of(10L));
            verifyNoMoreInteractions(orders);
        } finally {
            BaseContext.removeCurrentId();
        }
    }
}
