package com.orderhub.dto;

import jakarta.validation.constraints.NotNull;

public record InventoryAdjustmentRequest(@NotNull Integer quantityOnHand, String reason) {
}
