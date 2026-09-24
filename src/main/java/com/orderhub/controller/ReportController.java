package com.orderhub.controller;

import com.orderhub.dto.LowStockResponse;
import com.orderhub.dto.SalesSummaryResponse;
import com.orderhub.dto.TopProductResponse;
import com.orderhub.service.ReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports")
public class ReportController {

    private final ReportService reportService;
    private final int defaultLowStockThreshold;

    public ReportController(ReportService reportService,
                            @Value("${app.reporting.low-stock-threshold:5}") int defaultLowStockThreshold) {
        this.reportService = reportService;
        this.defaultLowStockThreshold = defaultLowStockThreshold;
    }

    @GetMapping("/sales-summary")
    public SalesSummaryResponse salesSummary(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.salesSummary(from, to);
    }

    @GetMapping("/top-products")
    public List<TopProductResponse> topProducts(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                @RequestParam(defaultValue = "10") int limit) {
        return reportService.topProducts(from, to, limit);
    }

    @GetMapping("/low-stock")
    public List<LowStockResponse> lowStock(@RequestParam(required = false) Integer threshold) {
        return reportService.lowStock(threshold != null ? threshold : defaultLowStockThreshold);
    }
}
