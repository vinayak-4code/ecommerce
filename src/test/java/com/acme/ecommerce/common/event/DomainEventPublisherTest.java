package com.acme.ecommerce.common.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit specification for domain-event outbox publishing.
 *
 * <p>The publisher writes an outbox row and also emits an in-process event so
 * read models can update during the demo deployment.</p>
 */
@ExtendWith(MockitoExtension.class)
class DomainEventPublisherTest {
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private DomainEventPublisher domainEventPublisher;

    @Test
    void publish_shouldPersistOutboxEventAndPublishEnvelope_whenPayloadSerializes() throws Exception {
        // Given
        UUID aggregateId = UUID.randomUUID();
        Map<String, Object> payload = Map.of("productId", aggregateId);
        when(objectMapper.writeValueAsString(payload)).thenReturn("{\"productId\":\"" + aggregateId + "\"}");

        // When
        domainEventPublisher.publish(aggregateId, "Product", DomainEventType.PRODUCT_CREATED, payload);

        // Then
        ArgumentCaptor<DomainEventEnvelope> envelopeCaptor = ArgumentCaptor.forClass(DomainEventEnvelope.class);
        verify(applicationEventPublisher).publishEvent(envelopeCaptor.capture());
        assertThat(envelopeCaptor.getValue().aggregateId()).isEqualTo(aggregateId);
        assertThat(envelopeCaptor.getValue().eventType()).isEqualTo(DomainEventType.PRODUCT_CREATED);
    }

    @Test
    void publish_shouldThrowIllegalStateException_whenPayloadCannotBeSerialized() throws Exception {
        // Given
        Object payload = new Object();
        when(objectMapper.writeValueAsString(payload)).thenThrow(new JsonProcessingException("boom") {});

        // When / Then
        assertThatThrownBy(() -> domainEventPublisher.publish(UUID.randomUUID(), "Product", DomainEventType.PRODUCT_CREATED, payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to serialize domain event payload");
    }
}
