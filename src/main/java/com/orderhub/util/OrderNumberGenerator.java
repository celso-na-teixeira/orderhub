package com.orderhub.util;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Generates human friendly order numbers such as {@code ORD-20260924-00042}.
 * The running number comes from a database sequence, so it is unique across restarts and application instances.
 */
@Component
public class OrderNumberGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final JdbcTemplate jdbcTemplate;

    public OrderNumberGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String next() {
        Long sequence = jdbcTemplate.queryForObject("select nextval('order_number_seq')", Long.class);
        return "ORD-" + LocalDate.now(ZoneOffset.UTC).format(DATE_FORMAT) + "-" + String.format("%05d", sequence);
    }
}
