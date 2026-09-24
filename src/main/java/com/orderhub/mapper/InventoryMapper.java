package com.orderhub.mapper;

import com.orderhub.domain.InventoryItem;
import com.orderhub.dto.InventoryResponse;
import com.orderhub.dto.LowStockResponse;
import org.springframework.stereotype.Component;

@Component
public class InventoryMapper {

    public InventoryResponse toResponse(InventoryItem item) {
        return new InventoryResponse(
                item.getProduct().getId(),
                item.getProduct().getSku(),
                item.getQuantityOnHand(),
                item.getQuantityReserved(),
                item.getAvailableQuantity(),
                item.getUpdatedAt());
    }

    public LowStockResponse toLowStock(InventoryItem item) {
        return new LowStockResponse(
                item.getProduct().getId(),
                item.getProduct().getSku(),
                item.getProduct().getName(),
                item.getAvailableQuantity());
    }
}
