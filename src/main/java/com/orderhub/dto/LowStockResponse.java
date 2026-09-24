package com.orderhub.dto;

public record LowStockResponse(Long productId, String sku, String name, int available) {
}
