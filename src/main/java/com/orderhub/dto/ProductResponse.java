package com.orderhub.dto;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        String description,
        String category,
        BigDecimal price,
        boolean active,
        Integer availableQuantity
) {
}
