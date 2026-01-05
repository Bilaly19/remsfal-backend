package de.remsfal.core.api;

import java.util.UUID;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import jakarta.ws.rs.core.Context;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;


@Path(NotificationEndpoint.CONTEXT + "/" + NotificationEndpoint.VERSION + "/" + NotificationEndpoint.SERVICE)
public interface NotificationEndpoint {

    String CONTEXT = "platform";
    String VERSION = "v1";
    String SERVICE = "notifications";

    @GET
    @Path("/subscribe")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @Operation(summary = "Subscribe to server-sent events for a context.")
    @APIResponse(responseCode = "401", description = "No user authentication provided via session cookie")
    void subscribe(
            @Parameter(description = "Context type (project, tenancy)", required = true)
            @QueryParam("contextType") String contextType,
            @Parameter(description = "Context identifier", required = true)
            @QueryParam("contextId") UUID contextId,
            @Context SseEventSink eventSink,
            @Context Sse sse);
}