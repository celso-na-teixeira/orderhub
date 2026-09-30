package com.orderhub.service;

import com.orderhub.domain.Discount;
import com.orderhub.domain.DiscountType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
    void fixedAmountDiscountNeverExceedsSubtotal() {
        Discount discount = discount(DiscountType.FIXED_AMOUNT, "20");

        BigDecimal result = calculator.calculate(discount, new BigDecimal("7.99"));

        assertThat(result).isEqualByComparingTo("7.99");
    }

    @ParameterizedTest 
    @CsvSource({
        "10, 200.00,  20.00",
        "10, 19.99,   2.00",
        "10, 1234.50, 123.45",
        "15, 42.50,   6.38",
        "15, 649.90,  97.49",
        "15, 5000.00, 750.00"
    })
    void percentageDiscountIsCalculatedOnSubtotalAndRoundedHalfUp(String pct, String subtotal, String expected) {
        Discount discount = discount(DiscountType.PERCENTAGE, pct);
        BigDecimal result = calculator.calculate(discount, new BigDecimal(subtotal));
        assertThat(result).isEqualTo(new BigDecimal(expected));
    }

    private static Discount discount(DiscountType type, String value) {
        Discount discount = new Discount();
        discount.setCode("TEST");
        discount.setType(type);
        discount.setValue(new BigDecimal(value));
        return discount;
    }
}
