package com.acme.ecommerce.notification.dto;

import com.acme.ecommerce.notification.enums.NotificationChannelType;
import com.acme.ecommerce.notification.enums.NotificationType;

import java.util.Map;
import java.util.UUID;

public record NotificationMessage(
        UUID recipientUserId,
        NotificationType type,
        NotificationChannelType channelType,
        String subject,
        String body,
        Map<String, Object> metadata
) {
}
