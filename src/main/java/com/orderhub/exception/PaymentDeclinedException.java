package com.orderhub.exception;

public class PaymentDeclinedException extends BusinessRuleException {

    public PaymentDeclinedException(String message) {
        super(message);
    }
}
