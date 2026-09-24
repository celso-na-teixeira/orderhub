package com.orderhub.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record SalesSummaryResponse(
        LocalDate from,
        LocalDate to,
        int orderCount,
        BigDecimal revenue,
        Map<String, BigDecimal> revenueByCategory
) {
}
