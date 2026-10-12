package com.orderhub.util;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderNumberGeneratorTest {

    @Test
    void generatesNumbersInExpectedFormatFromTheSequence() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(41L, 42L);
        OrderNumberGenerator generator = new OrderNumberGenerator(jdbcTemplate);

        String first = generator.next();
        String second = generator.next();

        assertThat(first).matches("ORD-\\d{8}-00041");
        assertThat(second).matches("ORD-\\d{8}-00042");
    }
}
