package com.acme.ecommerce.notification.channel;

import com.acme.ecommerce.notification.dto.NotificationMessage;
import com.acme.ecommerce.notification.enums.NotificationChannelType;

public interface NotificationChannel {
    NotificationChannelType channelType();

    void send(NotificationMessage message);
}
