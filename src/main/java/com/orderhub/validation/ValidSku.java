package com.orderhub.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * SKU format: three upper-case letters, a dash and 4-6 digits, e.g. {@code ELE-1001}.
 */
@Documented
@Constraint(validatedBy = SkuValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidSku {

    String message() default "must be a valid SKU (e.g. ELE-1001)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
