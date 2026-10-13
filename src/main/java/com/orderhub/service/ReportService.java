package com.orderhub.service;

import com.orderhub.domain.Order;
import com.orderhub.domain.OrderItem;
import com.orderhub.domain.OrderStatus;
import com.orderhub.dto.LowStockResponse;
import com.orderhub.dto.SalesSummaryResponse;
import com.orderhub.dto.TopProductResponse;
import com.orderhub.mapper.InventoryMapper;
import com.orderhub.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@Slf4j
@RequiredArgsConstructor
public class ReportService {

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final InventoryMapper inventoryMapper;

    public SalesSummaryResponse salesSummary(LocalDate from, LocalDate to) {
        List<Order> orders = orderRepository.findByCreatedAtBetween(from.atStartOfDay(), to.atStartOfDay());
        log.debug("Sales summary {} - {}: {} orders", from, to, orders.size());

        BigDecimal revenue = orders.stream()
                .filter(order -> order.getStatus() != OrderStatus.CANCELLED)
                .map(Order::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> revenueByCategory = orders.stream()
                .flatMap(order -> order.getItems().stream())
                .collect(Collectors.toMap(
                        (OrderItem item) -> item.getProduct().getCategory(),
                        OrderItem::getLineTotal));

        return new SalesSummaryResponse(from, to, orders.size(), revenue, revenueByCategory);
    }

    public List<TopProductResponse> topProducts(LocalDate from, LocalDate to, int limit) {
        return List.of();
    }

    public List<LowStockResponse> lowStock(int threshold) {
        return inventoryService.findLowStock(threshold).stream()
                .map(inventoryMapper::toLowStock)
                .toList();
    }
}
