package com.acme.ecommerce.notification.channel;

import com.acme.ecommerce.notification.dto.NotificationMessage;
import com.acme.ecommerce.notification.enums.NotificationChannelType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SmsNotificationChannel implements NotificationChannel {
    @Override
    public NotificationChannelType channelType() {
        return NotificationChannelType.SMS;
    }

    @Override
    public void send(NotificationMessage message) {
        log.info("SMS notification queued: type={}, recipient={}", message.type(), message.recipientUserId());
    }
}
