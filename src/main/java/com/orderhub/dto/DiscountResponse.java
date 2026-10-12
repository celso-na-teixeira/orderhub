package com.orderhub.dto;

import com.orderhub.domain.DiscountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DiscountResponse(
        Long id,
        String code,
        String description,
        DiscountType type,
        BigDecimal value,
        BigDecimal minOrderAmount,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        Integer maxUses,
        int usedCount,
        boolean active
) {
}
