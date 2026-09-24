package com.orderhub.service;

import com.orderhub.domain.Discount;
import com.orderhub.domain.DiscountType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DiscountCalculatorTest {

    private final DiscountCalculator calculator = new DiscountCalculator();

    @Test
    void fixedAmountDiscountReturnsConfiguredValue() {
        Discount discount = discount(DiscountType.FIXED_AMOUNT, "5.00");

        BigDecimal result = calculator.calculate(discount, new BigDecimal("80.00"));

        assertThat(result).isEqualByComparingTo("5.00");
    }

    @Test
    void percentageDiscountOnRoundAmount() {
        Discount discount = discount(DiscountType.PERCENTAGE, "10");

        BigDecimal result = calculator.calculate(discount, new BigDecimal("200.00"));

        assertThat(result).isEqualByComparingTo("20.00");
    }

    @Test
    void percentageDiscountIsRoundedToCents() {
        Discount discount = discount(DiscountType.PERCENTAGE, "10");

        BigDecimal result = calculator.calculate(discount, new BigDecimal("19.99"));

        assertThat(result).isEqualTo(new BigDecimal("2.00"));
    }

    @Test
    void fifteenPercentOfOddAmount() {
        Discount discount = discount(DiscountType.PERCENTAGE, "15");

        BigDecimal result = calculator.calculate(discount, new BigDecimal("42.50"));

        assertThat(result).isEqualTo(new BigDecimal("6.38"));
    }

    private static Discount discount(DiscountType type, String value) {
        Discount discount = new Discount();
        discount.setCode("TEST");
        discount.setType(type);
        discount.setValue(new BigDecimal(value));
        return discount;
    }
}
