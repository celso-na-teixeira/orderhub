package com.orderhub.dto;

import java.math.BigDecimal;

public record TopProductResponse(Long productId, String sku, String name, long unitsSold, BigDecimal revenue) {
}
