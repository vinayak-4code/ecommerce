package com.acme.ecommerce.notification.channel;

import com.acme.ecommerce.notification.dto.NotificationMessage;
import com.acme.ecommerce.notification.enums.NotificationChannelType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class InAppNotificationChannel implements NotificationChannel {
    @Override
    public NotificationChannelType channelType() {
        return NotificationChannelType.IN_APP;
    }

    @Override
    public void send(NotificationMessage message) {
        log.info("In-app notification stored: type={}, recipient={}", message.type(), message.recipientUserId());
    }
}
