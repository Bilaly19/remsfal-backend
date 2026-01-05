package de.remsfal.ticketing.control.notification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;

@ApplicationScoped
public class NotificationRegistry {

    private final ConcurrentHashMap<NotificationContext, ConcurrentHashMap<UUID,
            CopyOnWriteArraySet<NotificationConnection>>>
            connections = new ConcurrentHashMap<>();

    public NotificationConnection register(final NotificationContext context, final UUID userId,
                                           final SseEventSink sink, final Sse sse) {
        NotificationConnection connection = new NotificationConnection(context, userId, sink, sse);
        connections.computeIfAbsent(context, key -> new ConcurrentHashMap<>())
                .computeIfAbsent(userId, key -> new CopyOnWriteArraySet<>())
                .add(connection);
        return connection;
    }

    public void remove(final NotificationConnection connection) {
        remove(connection.getContext(), connection.getUserId(), connection.getSink());
    }

    public void remove(final NotificationContext context, final UUID userId, final SseEventSink sink) {
        Map<UUID, CopyOnWriteArraySet<NotificationConnection>> userConnections = connections.get(context);
        if (userConnections == null) {
            return;
        }
        CopyOnWriteArraySet<NotificationConnection> connectionsForUser = userConnections.get(userId);
        if (connectionsForUser != null) {
            connectionsForUser.removeIf(connection -> connection.getSink().equals(sink));
            if (connectionsForUser.isEmpty()) {
                userConnections.remove(userId);
            }
        }
        if (userConnections.isEmpty()) {
            connections.remove(context);
        }
    }

    public Collection<NotificationConnection> getConnections(final NotificationContext context) {
        Map<UUID, CopyOnWriteArraySet<NotificationConnection>> userConnections = connections.get(context);
        if (userConnections == null) {
            return List.of();
        }
        Collection<NotificationConnection> result = new ArrayList<>();
        userConnections.forEach((userId, connectionsForUser) -> connectionsForUser
                .forEach(result::add));
        return result;
    }

    public Collection<NotificationConnection> getAllConnections() {
        Collection<NotificationConnection> result = new ArrayList<>();
        connections.forEach((context, userConnections) ->
                userConnections.forEach((userId, connectionsForUser) ->
                        connectionsForUser.forEach(result::add)));
        return result;
    }

    int getConnectionCount() {
        return connections.values().stream()
                .mapToInt(map -> map.values().stream().mapToInt(Collection::size).sum())
                .sum();
    }
}