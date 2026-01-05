package de.remsfal.ticketing.control.notification;

import java.util.Objects;
import java.util.UUID;

public final class NotificationContext {

    private final NotificationContextType type;
    private final UUID id;

    public NotificationContext(final NotificationContextType type, final UUID id) {
        this.type = Objects.requireNonNull(type, "type");
        this.id = Objects.requireNonNull(id, "id");
    }

    public NotificationContextType getType() {
        return type;
    }

    public UUID getId() {
        return id;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NotificationContext that = (NotificationContext) o;
        return type == that.type && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, id);
    }

    @Override
    public String toString() {
        return type.getValue() + ":" + id;
    }
}