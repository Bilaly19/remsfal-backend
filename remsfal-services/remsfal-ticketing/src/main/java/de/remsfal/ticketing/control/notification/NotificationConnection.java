package de.remsfal.ticketing.control.notification;

import java.util.Objects;
import java.util.UUID;

import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;

public final class NotificationConnection {

    private final NotificationContext context;
    private final UUID userId;
    private final SseEventSink sink;
    private final Sse sse;

    public NotificationConnection(final NotificationContext context, final UUID userId, final SseEventSink sink,
                                  final Sse sse) {
        this.context = context;
        this.userId = userId;
        this.sink = sink;
        this.sse = sse;
    }

    public NotificationContext getContext() {
        return context;
    }

    public UUID getUserId() {
        return userId;
    }

    public SseEventSink getSink() {
        return sink;
    }

    public Sse getSse() {
        return sse;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NotificationConnection that = (NotificationConnection) o;
        return Objects.equals(context, that.context)
                && Objects.equals(userId, that.userId)
                && Objects.equals(sink, that.sink);
    }

    @Override
    public int hashCode() {
        return Objects.hash(context, userId, sink);
    }
}