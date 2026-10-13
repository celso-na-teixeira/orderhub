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
        if (next == null) {
            return false;
        }
        return switch (this) {
            case PENDING -> next == PAID || next == CANCELLED;
            case PAID -> next == SHIPPED || next == CANCELLED || next == REFUNDED;
            case SHIPPED -> next == DELIVERED;
            case DELIVERED -> next == REFUNDED;
            case CANCELLED, REFUNDED -> false;
        };
    }

    public boolean isTerminal() {
        return this == CANCELLED || this == REFUNDED;
    }
}
