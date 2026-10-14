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
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@Slf4j
@RequiredArgsConstructor
public class ReportService {

    /** Orders in these statuses count toward revenue (BR-80); PENDING, CANCELLED and REFUNDED do not. */
    private static final Set<OrderStatus> REVENUE_STATUSES =
            EnumSet.of(OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final InventoryMapper inventoryMapper;

    public SalesSummaryResponse salesSummary(LocalDate from, LocalDate to) {
        // the range is inclusive at both ends (BR-80), so the upper bound runs to the last instant of "to"
        List<Order> orders = orderRepository.findByCreatedAtBetween(from.atStartOfDay(), to.atTime(LocalTime.MAX));
        log.debug("Sales summary {} - {}: {} orders", from, to, orders.size());

        List<Order> revenueOrders = orders.stream()
                .filter(order -> REVENUE_STATUSES.contains(order.getStatus()))
                .toList();

        BigDecimal revenue = revenueOrders.stream()
                .map(Order::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> revenueByCategory = revenueOrders.stream()
                .flatMap(order -> order.getItems().stream())
                .collect(Collectors.toMap(
                        (OrderItem item) -> item.getProduct().getCategory(),
                        OrderItem::getLineTotal,
                        BigDecimal::add));

        return new SalesSummaryResponse(from, to, orders.size(), revenue, revenueByCategory);
    }

    public List<TopProductResponse> topProducts(LocalDate from, LocalDate to, int limit) {
        return orderRepository.topProducts(REVENUE_STATUSES, from.atStartOfDay(), to.atTime(LocalTime.MAX),
                PageRequest.of(0, limit));
    }

    public List<LowStockResponse> lowStock(int threshold) {
        return inventoryService.findLowStock(threshold).stream()
                .map(inventoryMapper::toLowStock)
                .toList();
    }
}
