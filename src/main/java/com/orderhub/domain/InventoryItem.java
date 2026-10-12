package com.orderhub.domain;

import com.orderhub.exception.BusinessRuleException;
import com.orderhub.exception.InsufficientStockException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_items")
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(name = "quantity_on_hand", nullable = false)
    private int quantityOnHand;

    @Column(name = "quantity_reserved", nullable = false)
    private int quantityReserved;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected InventoryItem() {
    }

    public InventoryItem(Product product, int quantityOnHand) {
        this.product = product;
        this.quantityOnHand = quantityOnHand;
        this.quantityReserved = 0;
        product.setInventory(this);
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }

    public int getAvailableQuantity() {
        return quantityOnHand - quantityReserved;
    }

    /**
     * Holds stock for a pending order. Reserved stock is still on hand but can no longer be sold to anyone else.
     */
    public void reserve(int quantity) {
        requirePositive(quantity);
        int available = getAvailableQuantity();
        if (available < quantity) {
            throw new InsufficientStockException(product.getId(), quantity, available);
        }
        quantityReserved += quantity;
    }

    /**
     * Turns reserved stock into a sale: it leaves the warehouse, so on hand and reserved both go down.
     */
    public void commit(int quantity) {
        requirePositive(quantity);
        if (quantity > quantityReserved) {
            throw new IllegalStateException("Cannot commit " + quantity + " units, only " + quantityReserved + " are reserved");
        }
        quantityOnHand -= quantity;
        quantityReserved -= quantity;
    }

    /**
     * Gives up a reservation that was never paid for. On hand is unchanged.
     */
    public void release(int quantity) {
        requirePositive(quantity);
        if (quantity > quantityReserved) {
            throw new IllegalStateException("Cannot release " + quantity + " units, only " + quantityReserved + " are reserved");
        }
        quantityReserved -= quantity;
    }

    /**
     * Puts committed stock back on hand, for example when a paid order is cancelled.
     */
    public void restock(int quantity) {
        requirePositive(quantity);
        quantityOnHand += quantity;
    }

    /**
     * Sets the on-hand quantity after a stock count. It cannot drop below what is currently reserved.
     */
    public void adjustOnHand(int newQuantityOnHand) {
        if (newQuantityOnHand < quantityReserved) {
            throw new BusinessRuleException("On-hand quantity cannot be set below the " + quantityReserved + " units currently reserved");
        }
        quantityOnHand = newQuantityOnHand;
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public int getQuantityOnHand() {
        return quantityOnHand;
    }

    public int getQuantityReserved() {
        return quantityReserved;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
