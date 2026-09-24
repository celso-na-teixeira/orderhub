package com.orderhub.dto;

import java.time.LocalDateTime;

public record InventoryResponse(
        Long productId,
        String sku,
        int quantityOnHand,
        int quantityReserved,
        int available,
        LocalDateTime updatedAt
) {
}
