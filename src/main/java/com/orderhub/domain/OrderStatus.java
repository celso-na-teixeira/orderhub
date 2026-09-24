package com.orderhub.domain;

/**
 * Lifecycle of an order. Declared in the order an order normally moves through.
 */
public enum OrderStatus {
    PENDING,
    PAID,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    REFUNDED;

    public boolean canTransitionTo(OrderStatus next) {
        if (next == null || next == this) {
            return false;
        }
        return next.ordinal() > this.ordinal();
    }

    public boolean isTerminal() {
        return this == CANCELLED || this == REFUNDED;
    }
}
