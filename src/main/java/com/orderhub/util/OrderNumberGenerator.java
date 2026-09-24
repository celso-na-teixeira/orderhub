package com.orderhub.util;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Generates human friendly order numbers such as {@code ORD-20260924-00042}.
 */
@Component
public class OrderNumberGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private int sequence = 0;

    public String next() {
        sequence++;
        return "ORD-" + LocalDate.now().format(DATE_FORMAT) + "-" + String.format("%05d", sequence);
    }
}
