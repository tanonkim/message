package com.message.domain.notification.repository;

import com.message.domain.notification.entity.NotificationLog;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static com.message.domain.notification.entity.QNotificationLog.notificationLog;

@Repository
@RequiredArgsConstructor
public class DetailNotificaionLogRepository {

    private final JPAQueryFactory queryFactory;

    public boolean existsByIdempotencyKey(String idempotencyKey) {
        return queryFactory
                .selectOne()
                .from(notificationLog)
                .where(notificationLog.idempotencyKey.eq(idempotencyKey))
                .fetchFirst() != null;
    }

    public Optional<NotificationLog> findByIdempotencyKey(String idempotencyKey) {
        return Optional.ofNullable(queryFactory
                .select(notificationLog)
                .where(notificationLog.idempotencyKey.eq(idempotencyKey))
                .fetchOne());

    }

}
