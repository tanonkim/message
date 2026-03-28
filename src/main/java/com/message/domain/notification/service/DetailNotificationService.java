package com.message.domain.notification.service;


import com.message.domain.notification.controller.response.NotificationResponse;
import com.message.domain.notification.entity.NotificationLog;
import com.message.domain.notification.repository.NotificationLogRepository;
import com.message.global.exception.ApiException;
import com.message.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DetailNotificationService {

    private final NotificationLogRepository notificationLogRepository;

    @Transactional(readOnly = true)
    public NotificationResponse getStatus(Long notificationLogId) {
        NotificationLog log = notificationLogRepository.findById(notificationLogId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOTIFICATION_NOT_FOUND));
        return NotificationResponse.from(log);
    }
}
