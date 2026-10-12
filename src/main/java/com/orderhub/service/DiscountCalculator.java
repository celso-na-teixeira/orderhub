package com.orderhub.service;

import com.orderhub.domain.Discount;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import com.orderhub.util.MoneyUtils;

@Component
public class DiscountCalculator {

    /**
     * Calculates the monetary discount for the given subtotal.
     */
    public BigDecimal calculate(Discount discount, BigDecimal subtotal) {
        return switch (discount.getType()) {
            case PERCENTAGE -> {
                yield subtotal.multiply(discount.getValue()).divide(MoneyUtils.ONE_HUNDRED, 2, RoundingMode.HALF_UP).min(subtotal);
            }
            case FIXED_AMOUNT -> discount.getValue().min(subtotal);
        };
    }
}
