package com.acme.ecommerce.notification.service;

import com.acme.ecommerce.common.event.DomainEventEnvelope;
import com.acme.ecommerce.common.event.DomainEventType;
import com.acme.ecommerce.notification.channel.NotificationChannel;
import com.acme.ecommerce.notification.dto.NotificationMessage;
import com.acme.ecommerce.notification.enums.NotificationChannelType;
import com.acme.ecommerce.notification.enums.NotificationType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class NotificationService {
    private final Map<NotificationChannelType, NotificationChannel> channels = new EnumMap<>(NotificationChannelType.class);
    private final ObjectMapper objectMapper;

    public NotificationService(List<NotificationChannel> notificationChannels, ObjectMapper objectMapper) {
        notificationChannels.forEach(channel -> channels.put(channel.channelType(), channel));
        this.objectMapper = objectMapper;
    }

    public void send(NotificationMessage message) {
        NotificationChannel channel = channels.get(message.channelType());
        if (channel == null) {
            throw new IllegalArgumentException("Unsupported notification channel: " + message.channelType());
        }
        channel.send(message);
    }

    @EventListener
    public void onDomainEvent(DomainEventEnvelope event) {
        if (event.eventType() != DomainEventType.ORDER_CREATED) {
            return;
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(event.payloadJson(), new TypeReference<>() {
            });
            Object customerId = payload.get("customerId");
            UUID recipientId = UUID.fromString(String.valueOf(customerId));
            send(new NotificationMessage(
                    recipientId,
                    NotificationType.ORDER_CONFIRMATION,
                    NotificationChannelType.IN_APP,
                    "Order confirmation",
                    "Your order has been placed successfully.",
                    payload
            ));
        } catch (Exception exception) {
            log.warn("Notification event handling failed for event {}", event.eventType(), exception);
        }
    }
}
