package com.orderhub.mapper;

import com.orderhub.domain.InventoryItem;
import com.orderhub.domain.Product;
import com.orderhub.dto.CreateProductRequest;
import com.orderhub.dto.ProductResponse;
import com.orderhub.dto.UpdateProductRequest;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public ProductResponse toResponse(Product product) {
        InventoryItem inventory = product.getInventory();
        Integer available = inventory != null ? inventory.getAvailableQuantity() : null;
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getCategory(),
                product.getPrice(),
                product.isActive(),
                available);
    }

    public Product toEntity(CreateProductRequest request) {
        Product product = new Product();
        product.setSku(request.sku());
        product.setName(request.name());
        product.setDescription(request.description());
        product.setCategory(request.category());
        product.setPrice(request.price());
        product.setActive(true);
        return product;
    }

    public void applyUpdate(UpdateProductRequest request, Product product) {
        product.setName(request.name());
        product.setDescription(request.description());
        product.setCategory(request.category());
        product.setPrice(request.price());
        product.setActive(request.active());
    }
}
