package com.arena.core.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    FACILITY_INVALID_REQUEST(400, "Review the highlighted facility fields."),
    FACILITY_VERSION_CONFLICT(409, "A facility was modified by another user. Review the latest configuration before saving."),
    FACILITY_CAPABILITY_DISABLED(409, "Enable Play & Facility Access on the active registered sport before configuring facilities."),
    FACILITY_UNSUPPORTED(400, "This sport or facility type is not operationally supported."),
    FACILITY_NOT_FOUND(404, "The facility was not found."),
    FACILITY_IDEMPOTENCY_CONFLICT(409, "This operation key was already used with a different request. Reconcile the original operation first."),
    FACILITY_COMMITMENT_EXISTS(409, "Ongoing or future commitments prevent this facility change."),
    FACILITY_SESSION_ACTIVE(409, "A running session prevents this facility from being deactivated."),
    FACILITY_NOT_ELIGIBLE(409, "This facility is inactive or its pricing is incomplete."),
    FACILITY_CONCURRENCY_FAILURE(409, "Concurrent facility changes prevented this operation. Refresh and retry safely."),

    INVALID_LOGO(400,"Upload a valid PNG, JPEG or WebP image (maximum 4096 pixels per side and 4 megapixels)."),
    LOGO_TOO_LARGE(413,"The logo must be at most 2 MB, including its normalized image."),
    SINGLE_SPORT_LIMIT_EXCEEDED(409,"Your club allows only one active sport. Deactivate your current sport before activating another."),
    SPORT_SESSION_ACTIVE(409,"Running or paused facility sessions prevent this change. Stop these sessions before deactivating the sport or disabling facility access."),
    SPORT_RESERVATION_EXISTS(409,"Reserved facility sessions prevent this change. Review and resolve these reservations before deactivating the sport or disabling facility access."),
    TRAINING_SPORT_DUPLICATE(409, "This sport or activity is already registered. Reactivate its existing registration."),
    TRAINING_DEPENDENCY_EXISTS(409, "This record is still used by a draft or active batch or coach assignment."),
    TRAINING_COACH_INVALID(400, "Select active coaches from this academy who are assigned to the batch sport."),
    TRAINING_ACTIVATION_INCOMPLETE(409, "An active batch requires at least one coach and one valid schedule."),
    TRAINING_SCHEDULE_INVALID(400, "Schedule times and effective dates must be valid and within the batch dates."),
    TRAINING_SCHEDULE_CONFLICT(409, "This schedule overlaps another entry or an active batch for an assigned coach."),
    TRAINING_MEMBERSHIP_EXISTS(409, "This person already belongs to this academy. Edit the existing membership."),
    STAFF_LOGIN_ACCESS_UNAVAILABLE(409, "Staff login access is not available yet. Staff registration does not create an account or send an invitation."),
  STAFF_INVITATION_FAILED_SAVED(502, "Staff membership was saved, but the invitation was not sent. Retry the invitation from Staff."),
    QR_CHECKIN_UNAVAILABLE(410, "This QR code or check-in is unavailable or expired. Ask staff for a new QR."),
    TOO_MANY_REQUESTS(429, "Too many check-in attempts. Please wait before trying again."),
    SESSION_QUOTE_STALE(409,"This stop quote expired, was already used, or the session changed. Refresh the quote before confirming."),
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
    DUPLICATE_USERNAME(409, "This username is already in use. Choose another username."),
    DUPLICATE_EMAIL(409, "An account already uses this email address. Enter a different email."),
    STAFF_REGISTRATION_UNCERTAIN(503, "Registration status could not be confirmed. Retry this request before adding another staff member."),
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
    PUBLIC_BOOKING_INVALID_MOBILE(400, "Enter a valid mobile number with its country code, for example +919876543210."),
    PUBLIC_BOOKING_INVALID_TIME(400, "Choose a valid local date and time in the club timezone."),
    PUBLIC_BOOKING_TIME_INCREMENT(400, "Choose a start time at :00, :15, :30 or :45, with no seconds."),
    PUBLIC_BOOKING_WINDOW(400, "Choose a start time at least 15 minutes and no more than 30 days from now."),
    PUBLIC_BOOKING_OUTSIDE_HOURS(400, "The entire reservation must fit within the club opening hours."),
    PUBLIC_BOOKING_DURATION(400, "Choose one of the booking durations offered by this club."),
    PUBLIC_BOOKING_DISABLED(400, "This club is not accepting reservation requests right now."),
    PUBLIC_BOOKING_INVALID_DETAILS(400, "Check the required booking details and use a local date and time without a timezone suffix."),
    PUBLIC_BOOKING_FACILITY_UNAVAILABLE(404, "That facility is unavailable for booking. Refresh and choose an available facility."),
    PUBLIC_BOOKING_CONFLICT(409, "That facility is already booked for the selected time. Choose another time or facility."),
    PUBLIC_SLUG_INVALID(400, "Choose a 3–40 character address using letters, numbers and single hyphens. System addresses are reserved."),
    PUBLIC_SLUG_UNAVAILABLE(409, "This website address is already assigned to another club. Choose a different address."),
    PUBLIC_SLUG_CHANGE_NOT_ALLOWED(409, "Acknowledge the address change and wait until the seven-day change limit ends."),
    PUBLIC_SITE_NOT_PUBLISHED(409, "Publish the club website in Public Website settings before showing a QR code. Quick Start remains available."),
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
