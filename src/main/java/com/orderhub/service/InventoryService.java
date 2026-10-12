package com.orderhub.service;

import com.orderhub.domain.InventoryItem;
import com.orderhub.exception.ResourceNotFoundException;
import com.orderhub.repository.InventoryRepository;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryRepository inventoryRepository;
    private final EntityManager entityManager;

    public InventoryService(InventoryRepository inventoryRepository, EntityManager entityManager) {
        this.inventoryRepository = inventoryRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public InventoryItem getByProductId(Long productId) {
        return findItem(productId);
    }

    /**
     * Holds stock for a pending order. Reserved stock is still physically on hand
     * but can no longer be sold to anyone else.
     */
    public void reserve(Long productId, int quantity) {
        InventoryItem item = lockItem(productId);
        item.reserve(quantity);
        log.debug("Reserved {} of product {} (on hand {}, reserved {})",
                quantity, productId, item.getQuantityOnHand(), item.getQuantityReserved());
    }

    /**
     * Converts a reservation into a sale once the order is paid.
     */
    public void commit(Long productId, int quantity) {
        InventoryItem item = lockItem(productId);
        item.commit(quantity);
        log.debug("Committed {} of product {}", quantity, productId);
    }

    /**
     * Frees the reservation of a pending order that is cancelled.
     */
    public void release(Long productId, int quantity) {
        InventoryItem item = lockItem(productId);
        item.release(quantity);
        log.debug("Released {} of product {}", quantity, productId);
    }

    /**
     * Returns committed stock to on hand when a paid order is cancelled.
     */
    public void restock(Long productId, int quantity) {
        InventoryItem item = lockItem(productId);
        item.restock(quantity);
        log.debug("Restocked {} of product {}", quantity, productId);
    }

    public InventoryItem adjust(Long productId, int newQuantityOnHand, String reason) {
        InventoryItem item = lockItem(productId);
        int previous = item.getQuantityOnHand();
        item.adjustOnHand(newQuantityOnHand);
        log.info("Stock adjusted for product {}: {} -> {} ({})", productId, previous, newQuantityOnHand, reason);
        return item;
    }

    @Transactional(readOnly = true)
    public List<InventoryItem> findLowStock(int threshold) {
        return inventoryRepository.findAll().stream()
                .filter(item -> item.getAvailableQuantity() <= threshold)
                .toList();
    }

    // every stock change locks the row first, so two checkouts cannot both take the last unit (BR-11)
    private InventoryItem lockItem(Long productId) {
        InventoryItem item = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory for product", productId));
        // if this transaction had already loaded the item, the query returns that older copy: re-read it under the lock
        entityManager.refresh(item);
        return item;
    }

    private InventoryItem findItem(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory for product", productId));
    }
}
