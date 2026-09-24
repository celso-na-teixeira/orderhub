package com.orderhub.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Stand-in for the external payment service provider. Tokens starting with
 * {@code tok_decline} are declined; everything else succeeds after a short delay.
 */
@Component
public class FakePaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(FakePaymentGateway.class);

    @Override
    public GatewayResult charge(String orderReference, BigDecimal amount, String cardToken) {
        simulateNetworkLatency();
        if (cardToken.startsWith("tok_decline")) {
            log.info("PSP declined charge of {} for {}", amount, orderReference);
            return new GatewayResult(false, null, "Card declined");
        }
        String reference = "psp_" + UUID.randomUUID().toString().substring(0, 8);
        log.info("PSP charged {} for {} ({})", amount, orderReference, reference);
        return new GatewayResult(true, reference, "Approved");
    }

    private void simulateNetworkLatency() {
        try {
            Thread.sleep(400);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
