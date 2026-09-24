package com.orderhub.dto;

import com.orderhub.domain.DiscountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DiscountRequest(
        @NotBlank String code,
        String description,
        @NotNull DiscountType type,
        @NotNull @Positive BigDecimal value,
        BigDecimal minOrderAmount,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        Integer maxUses,
        Boolean active
) {
}
