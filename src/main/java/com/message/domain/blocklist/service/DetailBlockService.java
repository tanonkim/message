package com.message.domain.blocklist.service;

import com.message.domain.blocklist.repository.BlocklistRepository;
import com.message.domain.notification.controller.request.NotificationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DetailBlockService {

    private final BlocklistRepository blocklistRepository;

    public boolean isBlocked(NotificationRequest notificationRequest) {
        NotificationRequest.RecipientDto recipient = notificationRequest.recipient();
        if (recipient.phone() != null && blocklistRepository.existsByCellPhone(recipient.phone())) {
            return true;
        }
        if (recipient.email() != null && blocklistRepository.existsByEmail(recipient.email())) {
            return true;
        }
        return false;
    }
}
