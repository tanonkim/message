package com.message.domain.template.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "notification_template")
public class NotificationTemplate {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long notificationTemplateId;

    @Column(unique = true, nullable = false)
    private String templateCode;

    @Column(nullable = false)
    private String channel;

    private String templateId;

    private String pfId;

    private String title;

    private String description;

    @Column(nullable = false)
    private boolean isActive;

    @Column(nullable = false)
    private LocalDateTime createAt;

    @Column(nullable = false)
    private LocalDateTime updateAt;
}
