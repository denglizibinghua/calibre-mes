package com.bikeassembly.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 非持久业务通知在事务提交后发出，回滚不发；不改变调用服务的事务边界。 */
@Slf4j
public final class AfterCommitNotification {
    private AfterCommitNotification() { }

    public static void execute(Runnable notification) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(notification);
                }
            });
        } else {
            send(notification);
        }
    }

    private static void send(Runnable notification) {
        try {
            notification.run();
        } catch (Exception e) {
            // 数据已经提交，通知失败不能让客户端误以为业务写入失败。
            log.error("业务实时通知失败，业务结果不受影响", e);
        }
    }
}
