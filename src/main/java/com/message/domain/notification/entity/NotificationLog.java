package com.message.domain.notification.entity;

import com.message.domain.notification.converter.NotificationChannelConverter;
import com.message.domain.notification.converter.NotificationPriorityConverter;
import com.message.domain.notification.converter.NotificationStatusConverter;
import com.message.domain.notification.enum_type.NotificationChannel;
import com.message.domain.notification.enum_type.NotificationPriority;
import com.message.domain.notification.enum_type.NotificationStatus;
import com.message.global.crypto.StringCryptoConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "notification_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_log_id", columnDefinition = "bigint comment 'PK'")
    private Long id;

    @Column(unique = true, columnDefinition = "varchar(100) comment '중복 방지 키'")
    private String idempotencyKey;

    @Column(nullable = false, columnDefinition = "varchar(50) comment '서비스 ID'")
    private String serviceId;

    @Convert(converter = NotificationChannelConverter.class)
    @Column(nullable = false, columnDefinition = "varchar(20) comment '발송 채널'")
    private NotificationChannel channel;

    @Convert(converter = NotificationPriorityConverter.class)
    @Column(nullable = false, columnDefinition = "varchar(20) comment '발송 우선순위'")
    private NotificationPriority priority;

    @Convert(converter = StringCryptoConverter.class)
    @Column(nullable = false, columnDefinition = "text comment '수신자 (암호화)'")
    private String recipient;

    @Column(columnDefinition = "varchar(100) comment '템플릿 ID'")
    private String templateId;

    @Column(columnDefinition = "text comment '템플릿 변수 (JSON)'")
    private String variables;

    @Convert(converter = NotificationStatusConverter.class)
    @Column(nullable = false, columnDefinition = "varchar(20) comment '발송 상태'")
    private NotificationStatus status;

    @Column(columnDefinition = "int default 0 comment '재시도 횟수'")
    private int retryCount;

    @Column(columnDefinition = "varchar(20) comment '폴백 채널'")
    private String fallbackChannel;

    @Column(columnDefinition = "varchar(100) comment '공급자 메시지 ID'")
    private String providerMessageId;

    @Column(columnDefinition = "text comment '오류 메시지'")
    private String errorMessage;

    @Column(columnDefinition = "decimal(10, 4) comment '발송 비용'")
    private BigDecimal cost;

    @Column(columnDefinition = "datetime comment '발송일시'")
    private LocalDateTime sentAt;

    @CreatedBy
    @Column(name = "create_id", updatable = false, columnDefinition = "bigint comment '작성자 ID'")
    private Long createBy;

    @LastModifiedBy
    @Column(name = "update_id", columnDefinition = "bigint comment '수정자 ID'")
    private Long modifyBy;

    @CreatedDate
    @Column(name = "create_at", nullable = false, updatable = false, columnDefinition = "datetime comment '등록일'")
    private LocalDateTime createAt;

    @LastModifiedDate
    @Column(name = "update_at", nullable = false, columnDefinition = "datetime comment '수정일'")
    private LocalDateTime updateAt;


    @Builder
    public NotificationLog(String idempotencyKey, String serviceId, NotificationChannel channel,
                           NotificationPriority priority, String recipient, String templateId,
                           String variables, String fallbackChannel) {
        this.idempotencyKey = idempotencyKey;
        this.serviceId = serviceId;
        this.channel = channel;
        this.priority = priority;
        this.recipient = recipient;
        this.templateId = templateId;
        this.variables = variables;
        this.fallbackChannel = fallbackChannel;
        this.status = NotificationStatus.PENDING;
        this.retryCount = 0;
        this.cost = BigDecimal.ZERO;
    }

    public void markSent(String providerMessageId, BigDecimal cost) {
        this.status = NotificationStatus.SENT;
        this.providerMessageId = providerMessageId;
        this.cost = cost;
        this.sentAt = LocalDateTime.now();
    }

    public void markFailed(String errorMessage) {
        this.status = NotificationStatus.FAILED;
        this.errorMessage = errorMessage;
        this.retryCount++;
    }

    public void markFallback(String fallbackChannel) {
        this.status = NotificationStatus.FALLBACK;
        this.fallbackChannel = fallbackChannel;
    }
}
