package com.orderhub.exception;

import com.orderhub.domain.OrderStatus;

public class InvalidOrderStateException extends BusinessRuleException {

    public InvalidOrderStateException(String message) {
        super(message);
    }

    public InvalidOrderStateException(Long orderId, OrderStatus from, OrderStatus to) {
        super("Order " + orderId + " cannot move from " + from + " to " + to);
    }
}
