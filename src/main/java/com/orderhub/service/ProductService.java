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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Transactional
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductMapper productMapper;

    public ProductService(ProductRepository productRepository,
                          InventoryRepository inventoryRepository,
                          ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.productMapper = productMapper;
    }

    @Cacheable(value = "products", key = "#id")
    @Transactional(readOnly = true)
    public Product getProduct(Long id) {
        log.debug("Loading product {} from database", id);
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    @Transactional(readOnly = true)
    public Page<Product> search(String name, String category, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {
        if (name == null && category == null) {
            return productRepository.findByActiveTrue(pageable);
        }
        return productRepository.findByNameContainingAndCategoryAndActiveTrue(name, category, pageable);
    }

    public Product create(CreateProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new ConflictException("Product with SKU " + request.sku() + " already exists");
        }
        Product product = productRepository.save(productMapper.toEntity(request));
        InventoryItem inventory = inventoryRepository.save(new InventoryItem(product, request.initialStock()));
        product.setInventory(inventory);
        log.info("Created product {} ({}) with initial stock {}", product.getSku(), product.getId(), request.initialStock());
        return product;
    }

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
