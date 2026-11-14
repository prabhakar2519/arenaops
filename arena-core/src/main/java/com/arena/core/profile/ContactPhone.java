package com.arena.core.profile;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.FIELD,ElementType.METHOD,ElementType.PARAMETER,ElementType.RECORD_COMPONENT,ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy=ContactPhoneValidator.class)
public @interface ContactPhone {
 String message() default "Enter a valid 10-digit mobile number starting with 6–9. +91 is optional.";
 Class<?>[] groups() default {};
 Class<? extends Payload>[] payload() default {};
}
