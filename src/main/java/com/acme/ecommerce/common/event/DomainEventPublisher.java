package com.acme.ecommerce.common.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DomainEventPublisher {
    private final OutboxEventRepository outboxEventRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper;

    public void publish(UUID aggregateId, String aggregateType, DomainEventType eventType, Object payload) {
        String payloadJson = toJson(payload);
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateId(aggregateId);
        outboxEvent.setAggregateType(aggregateType);
        outboxEvent.setEventType(eventType);
        outboxEvent.setPayloadJson(payloadJson);
        outboxEventRepository.save(outboxEvent);
        applicationEventPublisher.publishEvent(new DomainEventEnvelope(aggregateId, aggregateType, eventType, payloadJson));
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize domain event payload", exception);
        }
    }
}
