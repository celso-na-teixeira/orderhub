package com.orderhub.mapper;

import com.orderhub.domain.InventoryItem;
import com.orderhub.dto.InventoryResponse;
import com.orderhub.dto.LowStockResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface InventoryMapper {

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "sku", source = "product.sku")
    @Mapping(target = "available", source = "availableQuantity")
    InventoryResponse toResponse(InventoryItem item);

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "sku", source = "product.sku")
    @Mapping(target = "name", source = "product.name")
    @Mapping(target = "available", source = "availableQuantity")
    LowStockResponse toLowStock(InventoryItem item);
}
