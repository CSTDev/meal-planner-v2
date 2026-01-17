package uk.co.cstdev.data.messaging;

public record EventMetadata(
        String sourceService,
        String correlationId,
        String userId) {
}
