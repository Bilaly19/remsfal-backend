package de.remsfal.ticketing.boundary;

import java.util.UUID;

import org.jboss.logging.Logger;

import de.remsfal.common.authentication.RemsfalPrincipal;
import de.remsfal.core.api.NotificationEndpoint;
import de.remsfal.ticketing.control.notification.NotificationConnection;
import de.remsfal.ticketing.control.notification.NotificationContext;
import de.remsfal.ticketing.control.notification.NotificationContextType;
import de.remsfal.ticketing.control.notification.NotificationDispatcher;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import io.vertx.ext.web.RoutingContext;

/**
 * @author Alexander Stanik [alexander.stanik@htw-berlin.de]
 */
@Authenticated
@RequestScoped
public class NotificationResource implements NotificationEndpoint {

    @Inject
    RemsfalPrincipal principal;

    @Inject
    NotificationDispatcher dispatcher;

    @Inject
    Logger logger;

    @Context
    RoutingContext routingContext;

    @Override
    public void subscribe(final String contextType, final UUID contextId,
                          @Context final SseEventSink eventSink, @Context final Sse sse) {

        if (contextId == null) {
            throw new BadRequestException("contextId is required");
        }

        NotificationContextType type = NotificationContextType.fromString(contextType);
        if (type == null) {
            throw new BadRequestException("Unsupported contextType");
        }

        authorizeContext(type, contextId);

        NotificationContext context = new NotificationContext(type, contextId);

        // Register + send "connected"
        dispatcher.register(context, principal.getId(), eventSink, sse);


    }

    private void authorizeContext(final NotificationContextType type, final UUID contextId) {
        switch (type) {
            case PROJECT -> {
                boolean hasProjectRole = principal.getProjectRoles().containsKey(contextId);
                boolean hasTenancyProject = principal.getTenancyProjects().containsValue(contextId);
                if (!hasProjectRole && !hasTenancyProject) {
                    throw new ForbiddenException("User does not have permission to subscribe to project events");
                }
            }
            case TENANCY -> {
                if (!principal.getTenancyProjects().containsKey(contextId)) {
                    throw new ForbiddenException("User does not have permission to subscribe to tenancy events");
                }
            }
            default -> throw new BadRequestException("Unsupported contextType");
        }
    }
}