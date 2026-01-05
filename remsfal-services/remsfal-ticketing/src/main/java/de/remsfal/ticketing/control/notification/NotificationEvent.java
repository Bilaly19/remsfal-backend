package de.remsfal.ticketing.control.notification;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationEvent(
        String type,
        EventContext context,
        EventEntity entity,
        Map<String, Object> data,
        EventMeta meta) {

    public record EventContext(String type, String id) {
    }

    public record EventEntity(String type, String id) {
    }

    public record EventMeta(Instant timestamp, UUID actorId, String correlationId) {
    }
}