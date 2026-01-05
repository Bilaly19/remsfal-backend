package de.remsfal.ticketing.boundary;

import de.remsfal.core.model.UserModel;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jboss.logging.Logger;

import de.remsfal.core.api.ticketing.IssueEndpoint;
import de.remsfal.core.json.UserJson.UserRole;
import de.remsfal.core.json.ticketing.IssueJson;
import de.remsfal.core.json.ticketing.IssueListJson;
import de.remsfal.core.json.ticketing.IssueStatusJson;

import de.remsfal.core.model.project.RentalUnitModel.UnitType;
import de.remsfal.core.model.ProjectMemberModel;
import de.remsfal.core.model.ticketing.IssueModel;
import de.remsfal.core.model.ticketing.IssueModel.Status;
import de.remsfal.ticketing.control.notification.NotificationContextType;
import de.remsfal.ticketing.control.notification.NotificationDispatcher;
import de.remsfal.ticketing.control.notification.NotificationEvent;
import de.remsfal.ticketing.entity.dto.IssueEntity;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.core.HttpHeaders;

/**
 * @author Alexander Stanik [alexander.stanik@htw-berlin.de]
 */
@Authenticated
@RequestScoped
public class IssueResource extends AbstractResource implements IssueEndpoint {

    @Inject
    Logger logger;

    @Inject
    Instance<ChatSessionResource> chatSessionResource;

    @Inject
    NotificationDispatcher notificationDispatcher;

    @Context
    HttpHeaders headers;

    @Override
    public IssueListJson getIssues(Integer offset, Integer limit, UUID projectId, UUID ownerId, UUID tenancyId,
                                   UnitType rentalType, UUID rentalId, Status status) {
        logger.info("Yes i was called");
        List<UUID> projectFilter = null;
        if (projectId != null && principal.getProjectRoles().containsKey(projectId)) {
            projectFilter = List.of(projectId);
        } else {
            projectFilter = principal.getProjectRoles().keySet().stream().toList();
        }

        if (projectFilter.isEmpty()) {
            return getTenancyIssues(offset, limit, tenancyId, status);
        } else {
            return getProjectIssues(offset, limit, projectFilter, ownerId, tenancyId, rentalType, rentalId, status);
        }
    }

    private IssueListJson getProjectIssues(Integer offset, Integer limit, List<UUID> projectFilter, UUID ownerId,
                                           UUID tenancyId, UnitType rentalType, UUID rentalId,
                                           Status status) {
        final List<? extends IssueModel> issues =
                issueController.getIssues(projectFilter, ownerId, tenancyId, rentalType, rentalId,
                        status);
        return IssueListJson.valueOf(issues, 0, issues.size());
    }

    private IssueListJson getTenancyIssues(Integer offset, Integer limit, UUID tenancyId, Status status) {
        if (principal.getTenancyProjects().isEmpty()) {
            throw new NotFoundException("User is not a member of any tenancy");
        }
        if (tenancyId != null && !principal.getTenancyProjects().containsKey(tenancyId)) {
            throw new ForbiddenException("User does not have permission to view issues in this tenancy");
        }

        List<? extends IssueModel> issues;
        if (tenancyId != null && principal.getTenancyProjects().containsKey(tenancyId)) {
            issues = issueController.getIssuesOfTenancy(tenancyId);
        } else {
            issues = issueController.getIssuesOfTenancies(principal.getTenancyProjects().keySet());
        }

        if (status != null) {
            issues = issues.stream()
                    .filter(issue -> issue.getStatus() == status)
                    .toList();
        }
        return IssueListJson.valueOf(issues, 0, issues.size());
    }

    @Override
    public Response createIssue(final IssueJson issue) {
        UserRole principalRole = getPrincipalRole(issue.getProjectId());
        if (principalRole == null) {
            throw new ForbiddenException("User does not have permission to create issues in this project");
        }
        final IssueJson response;
        final UserModel user = principalAsUserModel();
        final IssueModel issueModel = issue;
        if (principalRole == UserRole.MANAGER) {
            response = IssueJson.valueOf(issueController.createIssue(user, issueModel));
        } else if (principalRole == UserRole.TENANT) {
            response = IssueJson.valueOfFiltered(
                    issueController.createIssue(user, issueModel, IssueModel.Status.PENDING));
        } else {
            throw new ForbiddenException("User does not have permission to create issues in this project");
        }
        NotificationEvent createdEvent = buildIssueEvent(
                "issue.created",
                response.getProjectId(),
                response.getId(),
                Map.of("issue", response));
        notificationDispatcher.dispatch(createdEvent);
        final URI location = uri.getAbsolutePathBuilder().path(issue.getProjectId().toString()).build();
        return Response.created(location)
                .type(MediaType.APPLICATION_JSON)
                .entity(response)
                .build();
    }

    @Override
    public IssueJson getIssue(final UUID issueId) {
        IssueModel issue = issueController.getIssue(issueId);
        if (principal.getProjectRoles().containsKey(issue.getProjectId())) {
            return IssueJson.valueOf(issue);
        } else if (principal.getTenancyProjects().containsKey(issue.getTenancyId())) {
            return IssueJson.valueOfFiltered(issue);
        }
        throw new ForbiddenException("User does not have permission to view this issue");
    }

    @Override
    public IssueJson updateIssue(final UUID issueId, final IssueJson issue) {
        IssueEntity entity = issueController.getIssue(issueId);
        if (!principal.getProjectRoles().containsKey(entity.getProjectId())) {
            throw new ForbiddenException("User does not have permission to update this issue");
        }
        IssueJson updated = IssueJson.valueOf(issueController.updateIssue(entity.getKey(), issue));
        NotificationEvent updatedEvent = buildIssueEvent(
                "issue.updated",
                updated.getProjectId(),
                updated.getId(),
                Map.of("issue", updated));
        notificationDispatcher.dispatch(updatedEvent);
        return updated;
    }
    @Override
    public IssueJson updateIssueStatus(final UUID issueId, final IssueStatusJson status) {
        checkWritePermissions(issueId);
        IssueEntity entity = issueController.getIssue(issueId);
        IssueModel updated = issueController.updateIssueStatus(entity.getKey(), status.getStatus());

        String correlationId = headers != null ? headers.getHeaderString("X-Correlation-ID") : null;
        NotificationEvent event = new NotificationEvent(
                "issue.status.changed",
                new NotificationEvent.EventContext(NotificationContextType.PROJECT.getValue(),
                        entity.getProjectId().toString()),
                new NotificationEvent.EventEntity("issue", entity.getId().toString()),
                Map.of("status", updated.getStatus()),
                new NotificationEvent.EventMeta(Instant.now(), principal.getId(), correlationId));
        notificationDispatcher.dispatch(event);

        return IssueJson.valueOf(updated);
    }

    @Override
    public void deleteIssue(final UUID issueId) {
        IssueEntity entity = issueController.getIssue(issueId);
        if (principal.getProjectRoles().containsKey(entity.getProjectId())) {
            issueController.deleteIssue(entity.getKey());
        } else if (principal.getTenancyProjects().containsKey(entity.getTenancyId())) {
            issueController.closeIssue(entity.getKey());
        } else {
            throw new ForbiddenException("User does not have permission to delete this issue");
        }
        NotificationEvent deletedEvent = buildIssueEvent(
                "issue.deleted",
                entity.getProjectId(),
                entity.getId(),
                Map.of(
                        "issueId", entity.getId().toString(),
                        "deleted", true));
        notificationDispatcher.dispatch(deletedEvent);
    }

    @Override
    public ChatSessionResource getChatSessionResource() {
        return resourceContext.initResource(chatSessionResource.get());
    }
    private NotificationEvent buildIssueEvent(final String type, final UUID projectId, final UUID issueId,
                                              final Map<String, Object> data) {
        String correlationId = headers != null ? headers.getHeaderString("X-Correlation-ID") : null;
        return new NotificationEvent(
                type,
                new NotificationEvent.EventContext(NotificationContextType.PROJECT.getValue(), projectId.toString()),
                new NotificationEvent.EventEntity("issue", issueId.toString()),
                data,
                new NotificationEvent.EventMeta(Instant.now(), principal.getId(), correlationId));
    }

    private UserModel principalAsUserModel() {
        return new UserModel() {
            @Override
            public UUID getId() {
                return principal.getId();
            }

            @Override
            public String getEmail() {
                return principal.getEmail();
            }

            @Override
            public String getName() {
                return principal.getName();
            }

            @Override
            public Boolean isActive() {
                return principal.isActive();
            }
        };
    }

}