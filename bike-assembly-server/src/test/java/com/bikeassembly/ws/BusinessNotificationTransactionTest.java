package com.bikeassembly.ws;

import com.bikeassembly.entity.*;
import com.bikeassembly.mapper.ProWorkOrderMapper;
import com.bikeassembly.service.MessageProducerService;
import com.bikeassembly.service.impl.*;
import com.bikeassembly.vo.QmsInspectionVO;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证五类真实服务通知入口遵循 Spring 提交/回滚语义；不替代数据库集成测试。 */
class BusinessNotificationTransactionTest {
    record Case(Class<?> service, String method, Object argument, String mqMethod) { }
    static Stream<Case> cases() {
        return Stream.of(
                new Case(AndonAlertServiceImpl.class, "notifyAndon", new AndonAlert(), "sendAndonMessage"),
                new Case(EqmRepairOrderServiceImpl.class, "notifyRepair", new EqmRepairOrder(), "sendEquipmentMessage"),
                new Case(QmsInspectionServiceImpl.class, "notifyQuality", new QmsInspectionVO(), "sendQualityMessage"),
                new Case(WmsStockDocServiceImpl.class, "notifyInventory", new WmsStockDoc(), "sendInventoryMessage"),
                new Case(ProWorkOrderServiceImpl.class, "notifyWorkOrder", 1L, "sendWorkOrderMessage"));
    }

    @ParameterizedTest @MethodSource("cases")
    void synchronousNotificationsOnlyAppearAfterCommitAndNeverAfterRollback(Case c) throws Exception {
        WebSocketNotifier notifier = mock(WebSocketNotifier.class);
        Object service = service(c, notifier, null);
        var tx = transaction();
        tx.executeWithoutResult(status -> {
            invoke(service, c);
            verifyNoInteractions(notifier);
            status.setRollbackOnly();
        });
        verifyNoInteractions(notifier);
        tx.executeWithoutResult(status -> {
            invoke(service, c);
            verifyNoInteractions(notifier);
        });
        verify(notifier).pushToAdmin(anyString(), eq("COMPLETE"), anyMap());
    }

    @ParameterizedTest @MethodSource("cases")
    void mqAlsoWaitsForCommitAndDoesNotDoublePush(Case c) throws Exception {
        WebSocketNotifier notifier = mock(WebSocketNotifier.class);
        MessageProducerService producer = mock(MessageProducerService.class);
        Object service = service(c, notifier, producer);
        transaction().executeWithoutResult(status -> {
            invoke(service, c);
            verifyNoInteractions(producer, notifier);
            status.setRollbackOnly();
        });
        verifyNoInteractions(producer, notifier);
        transaction().executeWithoutResult(status -> {
            invoke(service, c);
            verifyNoInteractions(producer, notifier);
        });
        assertEquals(1, mockingDetails(producer).getInvocations().size());
        assertEquals(c.mqMethod(), mockingDetails(producer).getInvocations().iterator().next().getMethod().getName());
        verifyNoInteractions(notifier);
    }

    @ParameterizedTest @MethodSource("cases")
    void noTransactionNotifiesImmediatelyAndMqFailureFallsBack(Case c) throws Exception {
        WebSocketNotifier notifier = mock(WebSocketNotifier.class);
        MessageProducerService producer = mock(MessageProducerService.class, invocation -> {
            throw new IllegalStateException("test broker unavailable");
        });
        Object service = service(c, notifier, producer);
        invoke(service, c);
        verify(notifier).pushToAdmin(anyString(), eq("COMPLETE"), anyMap());
    }

    private Object service(Case c, WebSocketNotifier notifier, MessageProducerService producer) throws Exception {
        Object service = c.service().getDeclaredConstructor().newInstance();
        ReflectionTestUtils.setField(service, "webSocketNotifier", notifier);
        @SuppressWarnings("unchecked") ObjectProvider<MessageProducerService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(producer);
        ReflectionTestUtils.setField(service, "messageProducerProvider", provider);
        if (service instanceof ProWorkOrderServiceImpl) {
            ProWorkOrderMapper mapper = mock(ProWorkOrderMapper.class);
            ProWorkOrder order = new ProWorkOrder();
            order.setWorkOrderId(1L);
            when(mapper.selectById(1L)).thenReturn(order);
            ReflectionTestUtils.setField(service, "proWorkOrderMapper", mapper);
        }
        return service;
    }

    private void invoke(Object service, Case c) {
        ReflectionTestUtils.invokeMethod(service, c.method(), c.argument(), "COMPLETE");
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(new AbstractPlatformTransactionManager() {
            @Override protected Object doGetTransaction() { return new Object(); }
            @Override protected void doBegin(Object tx, TransactionDefinition definition) { }
            @Override protected void doCommit(DefaultTransactionStatus status) { }
            @Override protected void doRollback(DefaultTransactionStatus status) { }
        });
    }
}
