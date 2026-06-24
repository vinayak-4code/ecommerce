package com.acme.ecommerce.catalog.dto;

import java.time.Instant;
import java.util.UUID;

public record ProductVersionResponse(
        UUID id,
        UUID productId,
        int versionNumber,
        String snapshotJson,
        Instant createdAt,
        UUID createdBy
) {
}
