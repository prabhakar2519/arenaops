package com.arena.core.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    INVALID_REQUEST(400, "The request is invalid."),
    VALIDATION_FAILED(400, "The request contains invalid fields."),
    AUTHENTICATION_REQUIRED(401, "Please sign in to continue."),
    AUTHENTICATION_FAILED(401, "Your sign-in session is invalid or expired. Please sign in again."),
    ACCESS_DENIED(403, "You do not have permission to perform this operation."),
    USER_DISABLED(403, "Your user account is disabled. Please contact your administrator."),
    CUSTOMER_ACCESS_BLOCKED(403, "Your ArenaOps account does not currently have access. Please contact your administrator."),
    SUBSCRIPTION_EXPIRED(403, "Your ArenaOps subscription has expired. Please contact your administrator."),
    SUBSCRIPTION_ACCESS_BLOCKED(403, "Your ArenaOps subscription is no longer active. Please contact your administrator."),
    PAYMENT_REQUIRED(403, "Your trial has ended. Please contact your administrator to continue."),
    CUSTOMER_NOT_FOUND(404, "Customer with the supplied identifier was not found."),
    CUSTOMER_ALREADY_EXISTS(409, "A customer already exists with this email."),
    USER_ALREADY_EXISTS(409, "An account already exists with these details."),
    INVITATION_NOT_FOUND(404, "No invitation was found."),
    INVITATION_INVALID(400, "The activation code is invalid."),
    INVITATION_EXPIRED(409, "This invitation has expired. Please request a new invitation."),
    INVITATION_ALREADY_CONSUMED(409, "This invitation has already been used."),
    INVITATION_REVOKED(409, "This invitation has been revoked. Please request a new invitation."),
    INVITATION_EMAIL_MISMATCH(400, "Registration email must match the invited email."),
    INVITATION_NOT_READY(409, "This invitation is not available for registration."),
    SUBSCRIPTION_NOT_FOUND(404, "No subscription was found for this customer."),
    SUBSCRIPTION_CANCELLED(409, "Cancelled customers must be reactivated before recording payment."),
    GRACE_PERIOD_ALREADY_ACTIVE(409, "Customer already has an active grace period."),
    GRACE_PERIOD_NOT_ALLOWED(409, "Grace period can only be granted when payment is due."),
    INVALID_GRACE_END(400, "Grace period end must be in the future."),
    INVALID_STATE(409, "This operation is not allowed in the current state."),
    RESOURCE_NOT_FOUND(404, "The requested resource was not found."),
    CONFLICT(409, "The operation conflicts with existing data. Please refresh and try again."),
    EMAIL_DISABLED(503, "Invitation email was not sent because email delivery is currently disabled."),
    EMAIL_DELIVERY_FAILED(503, "The invitation email could not be sent. Please retry the invitation."),
    IDENTITY_PROVIDER_UNAVAILABLE(503, "Sign-in services are temporarily unavailable. Please try again shortly."),
    IDENTITY_CREATION_FAILED(502, "Your account could not be created in the sign-in service. Please try again."),
    SERVICE_UNAVAILABLE(503, "ArenaOps is temporarily unavailable. Please try again shortly."),
    INTERNAL_SERVER_ERROR(500, "An unexpected error occurred while processing the request."),
    METHOD_NOT_ALLOWED(405, "This request method is not supported."),
    UNSUPPORTED_MEDIA_TYPE(415, "This request format is not supported."),
    NOT_ACCEPTABLE(406, "The requested response format is not supported.");

    private final int status;
    private final String description;

    public static ErrorCode forStatus(int status) {
        return switch (status) {
            case 400 -> INVALID_REQUEST;
            case 401 -> AUTHENTICATION_REQUIRED;
            case 403 -> ACCESS_DENIED;
            case 404 -> RESOURCE_NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 406 -> NOT_ACCEPTABLE;
            case 409 -> CONFLICT;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            case 502, 503, 504 -> SERVICE_UNAVAILABLE;
            default -> status >= 500 ? INTERNAL_SERVER_ERROR : INVALID_REQUEST;
        };
    }
}
