package com.orderhub.service;

import java.math.BigDecimal;

public interface PaymentGateway {

    GatewayResult charge(String orderReference, BigDecimal amount, String cardToken);

    record GatewayResult(boolean success, String reference, String message) {
    }
}
