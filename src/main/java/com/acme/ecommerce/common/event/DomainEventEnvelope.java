package com.acme.ecommerce.common.event;

import java.util.UUID;

public record DomainEventEnvelope(
        UUID aggregateId,
        String aggregateType,
        DomainEventType eventType,
        String payloadJson
) {
}
