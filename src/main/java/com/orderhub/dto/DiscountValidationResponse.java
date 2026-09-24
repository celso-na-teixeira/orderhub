package com.orderhub.dto;

import java.math.BigDecimal;

public record DiscountValidationResponse(String code, boolean valid, BigDecimal discountAmount, String message) {
}
