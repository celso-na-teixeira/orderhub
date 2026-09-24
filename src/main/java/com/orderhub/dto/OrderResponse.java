package com.orderhub.dto;

import com.orderhub.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String orderNumber,
        Long customerId,
        OrderStatus status,
        List<OrderItemResponse> items,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal total,
        String discountCode,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
