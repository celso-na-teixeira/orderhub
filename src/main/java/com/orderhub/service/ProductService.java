package com.orderhub.service;

import com.orderhub.domain.InventoryItem;
import com.orderhub.domain.Product;
import com.orderhub.dto.CreateProductRequest;
import com.orderhub.dto.UpdateProductRequest;
import com.orderhub.exception.ConflictException;
import com.orderhub.exception.ResourceNotFoundException;
import com.orderhub.mapper.ProductMapper;
import com.orderhub.repository.InventoryRepository;
import com.orderhub.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductMapper productMapper;

    @Cacheable(value = "products", key = "#id")
    @Transactional(readOnly = true)
    public Product getProduct(Long id) {
        log.debug("Loading product {} from database", id);
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    /**
     * Uncached read for responses that show stock: availability changes without the product itself being updated.
     */
    @Transactional(readOnly = true)
    public Product getProductWithStock(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    @Transactional(readOnly = true)
    public Page<Product> search(String name, String category, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {
        Specification<Product> spec = (root, query, cb) -> cb.isTrue(root.get("active"));
        if (name != null && !name.isBlank()) {
            String pattern = "%" + name.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern));
        }
        if (category != null && !category.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("category"), category));
        }
        if (minPrice != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), minPrice));
        }
        if (maxPrice != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), maxPrice));
        }
        return productRepository.findAll(spec, pageable);
    }

    public Product create(CreateProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new ConflictException("Product with SKU " + request.sku() + " already exists");
        }
        Product product = productRepository.save(productMapper.toEntity(request));
        inventoryRepository.save(new InventoryItem(product, request.initialStock()));
        log.info("Created product {} ({}) with initial stock {}", product.getSku(), product.getId(), request.initialStock());
        return product;
    }

    @CacheEvict(value = "products", key = "#id")
    public Product update(Long id, UpdateProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        productMapper.applyUpdate(request, product);
        log.info("Updated product {}", product.getSku());
        return product;
    }

    @CacheEvict(value = "products", key = "#id")
    public void deactivate(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        product.setActive(false);
        log.info("Deactivated product {}", product.getSku());
    }
}
