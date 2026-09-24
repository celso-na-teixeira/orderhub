package com.orderhub.dto;

import com.orderhub.validation.ValidSku;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateProductRequest(
        @NotBlank @ValidSku String sku,
        @NotBlank @Size(max = 200) String name,
        String description,
        @NotBlank String category,
        @NotNull @DecimalMin(value = "0.01") BigDecimal price,
        @PositiveOrZero int initialStock
) {
}
