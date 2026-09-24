package com.orderhub.dto;

import com.orderhub.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        Long orderId,
        BigDecimal amount,
        PaymentStatus status,
        String providerReference,
        LocalDateTime createdAt
) {
}
