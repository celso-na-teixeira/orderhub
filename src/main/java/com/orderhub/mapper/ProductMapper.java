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
        Product product = new Product(request.sku(), request.name(), request.category(), request.price());
        product.setDescription(request.description());
        return product;
    }

    /**
     * Partial update: only the fields present in the request change.
     */
    public void applyUpdate(UpdateProductRequest request, Product product) {
        if (request.name() != null) {
            product.setName(request.name());
        }
        if (request.description() != null) {
            product.setDescription(request.description());
        }
        if (request.category() != null) {
            product.setCategory(request.category());
        }
        if (request.price() != null) {
            product.setPrice(request.price());
        }
        if (request.active() != null) {
            product.setActive(request.active());
        }
    }
}
