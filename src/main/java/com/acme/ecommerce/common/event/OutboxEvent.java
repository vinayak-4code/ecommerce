package com.acme.ecommerce.common.event;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "outbox_events", indexes = {
        @Index(name = "idx_outbox_status_created", columnList = "status, created_at"),
        @Index(name = "idx_outbox_aggregate", columnList = "aggregate_type, aggregate_id")
})
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "aggregate_type", nullable = false, length = 80)
    private String aggregateType;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 80)
    private DomainEventType eventType;

    @Column(name = "payload_json", nullable = false, columnDefinition = "text")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "published_at")
    private Instant publishedAt;
}
