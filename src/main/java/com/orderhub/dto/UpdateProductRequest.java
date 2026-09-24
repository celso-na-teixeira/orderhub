package com.orderhub.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateProductRequest(
        @Size(max = 200) String name,
        String description,
        String category,
        @DecimalMin(value = "0.01") BigDecimal price,
        Boolean active
) {
}
