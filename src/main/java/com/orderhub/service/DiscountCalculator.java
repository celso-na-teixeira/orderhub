package com.orderhub.service;

import com.orderhub.domain.Discount;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DiscountCalculator {

    /**
     * Calculates the monetary discount for the given subtotal.
     */
    public BigDecimal calculate(Discount discount, BigDecimal subtotal) {
        return switch (discount.getType()) {
            case PERCENTAGE -> {
                double percentage = discount.getValue().doubleValue();
                yield BigDecimal.valueOf(subtotal.doubleValue() * percentage / 100);
            }
            case FIXED_AMOUNT -> discount.getValue();
        };
    }
}
