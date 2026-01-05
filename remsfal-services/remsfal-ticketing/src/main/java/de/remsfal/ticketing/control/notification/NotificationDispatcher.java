package de.remsfal.ticketing.control.notification;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@ApplicationScoped
public class NotificationDispatcher {

    private final NotificationRegistry registry;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler;
    private final Duration heartbeatInterval;

    @Inject
    Logger logger;

    @Inject
    public NotificationDispatcher(final NotificationRegistry registry,
                                  final ObjectMapper objectMapper,
                                  @ConfigProperty(name = "remsfal.notifications.heartbeat-interval", defaultValue = "15S")
                                  final Duration heartbeatInterval) {
        this(registry, objectMapper, heartbeatInterval,
                Executors.newSingleThreadScheduledExecutor());
    }

    NotificationDispatcher(final NotificationRegistry registry,
                           final ObjectMapper objectMapper,
                           final Duration heartbeatInterval,
                           final ScheduledExecutorService scheduler) {
        this.registry = registry;
        this.objectMapper = objectMapper;
        this.heartbeatInterval = heartbeatInterval;
        this.scheduler = scheduler;
    }

    @PostConstruct
    void startHeartbeat() {
        if (scheduler == null) {
            return;
        }
        scheduler.scheduleAtFixedRate(this::sendHeartbeat,
                heartbeatInterval.toSeconds(),
                heartbeatInterval.toSeconds(),
                TimeUnit.SECONDS);
    }

    @PreDestroy
    void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    public NotificationConnection register(final NotificationContext context, final UUID userId,
                                           final SseEventSink sink, final Sse sse) {
        NotificationConnection connection = registry.register(context, userId, sink, sse);
        NotificationEvent connectedEvent = new NotificationEvent(
                "connected",
                new NotificationEvent.EventContext(context.getType().getValue(), context.getId().toString()),
                new NotificationEvent.EventEntity("subscription", context.toString()),
                Map.of("message", "connected"),
                new NotificationEvent.EventMeta(Instant.now(), userId, null));
        sendEvent(connection, connectedEvent);
        return connection;
    }

    public void unregister(final NotificationConnection connection) {
        registry.remove(connection);
        closeQuietly(connection.getSink());
    }

    public void dispatch(final NotificationEvent event) {
        NotificationContextType contextType = NotificationContextType.fromString(event.context().type());
        if (contextType == null) {
            getLogger().warnf("Ignoring notification with unsupported context type: %s", event.context().type());
            return;
        }
        NotificationContext context = new NotificationContext(
                contextType,
                UUID.fromString(event.context().id()));
        registry.getConnections(context).forEach(connection -> sendEvent(connection, event));
    }

    public void sendHeartbeat() {
        Map<String, Object> payload = Map.of("ts", Instant.now().toString());
        registry.getAllConnections().forEach(connection -> {
            if (connection.getSse() == null) {
                registry.remove(connection);
                return;
            }
            OutboundSseEvent event = connection.getSse().newEventBuilder()
                    .name("ping")
                    .data(String.class, serialize(payload))
                    .mediaType(MediaType.APPLICATION_JSON_TYPE)
                    .build();
            sendEvent(connection, event);
        });
    }

    private void sendEvent(final NotificationConnection connection, final NotificationEvent event) {
        if (connection.getSse() == null) {
            registry.remove(connection);
            return;
        }
        OutboundSseEvent sseEvent = connection.getSse().newEventBuilder()
                .name(event.type())
                .data(String.class, serialize(event))
                .mediaType(MediaType.APPLICATION_JSON_TYPE)
                .build();
        sendEvent(connection, sseEvent);
    }

    private void sendEvent(final NotificationConnection connection, final OutboundSseEvent event) {
        SseEventSink sink = connection.getSink();
        if (sink.isClosed()) {
            registry.remove(connection);
            return;
        }
        CompletionStage<?> stage = sink.send(event);
        if (stage == null) {
            registry.remove(connection);
            return;
        }
        stage.whenComplete((ignored, error) -> {
            if (error != null) {
                getLogger().warnf("Removing SSE connection after send error: %s", error.getMessage());
                registry.remove(connection);
            }
        });
    }

    private String serialize(final Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            getLogger().warn("Failed to serialize SSE payload", e);
            return "{}";
        }
    }

    private void closeQuietly(final SseEventSink sink) {
        if (sink == null || sink.isClosed()) {
            return;
        }
        sink.close();
    }

    private Logger getLogger() {
        return logger != null ? logger : Logger.getLogger(NotificationDispatcher.class);
    }
}