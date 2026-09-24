package com.orderhub.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderNumberGeneratorTest {

    @Test
    void generatesSequentialNumbersInExpectedFormat() {
        OrderNumberGenerator generator = new OrderNumberGenerator();

        String first = generator.next();
        String second = generator.next();

        assertThat(first).matches("ORD-\\d{8}-\\d{5}");
        assertThat(second).isNotEqualTo(first);
    }
}
