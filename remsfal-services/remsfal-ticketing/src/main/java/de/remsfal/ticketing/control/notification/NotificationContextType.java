package de.remsfal.ticketing.control.notification;

import java.util.Locale;

public enum NotificationContextType {
    PROJECT("project"),
    TENANCY("tenancy");

    private final String value;

    NotificationContextType(final String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static NotificationContextType fromString(final String rawValue) {
        if (rawValue == null) {
            return null;
        }
        final String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        for (NotificationContextType type : values()) {
            if (type.value.equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}