package com.orderhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.orderhub.domain.Customer;
import com.orderhub.domain.Order;
import com.orderhub.domain.OrderItem;
import com.orderhub.domain.OrderStatus;
import com.orderhub.domain.Product;
import com.orderhub.dto.SalesSummaryResponse;
import com.orderhub.dto.TopProductResponse;
import com.orderhub.mapper.InventoryMapper;
import com.orderhub.repository.OrderRepository;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private InventoryMapper inventoryMapper;

    @InjectMocks
    private ReportService reportService;

    private static Order orderOf(OrderStatus status, String category, String price) {
        Order order = new Order();
        order.setStatus(status);
        order.setCustomer(new Customer(null, "Carol", "carol@example.com"));
        order.addItem(new OrderItem(new Product("SKU-" + category, "Item", category, new BigDecimal(price)),
                1, new BigDecimal(price)));
        order.applyPricing(null, BigDecimal.ZERO);
        return order;
    }

    @Test
    void revenueOnlyCountsPaidShippedAndDeliveredOrders() {
        List<Order> orders = List.of(
                orderOf(OrderStatus.PENDING, "KITCHEN", "10.00"),
                orderOf(OrderStatus.PAID, "KITCHEN", "20.00"),
                orderOf(OrderStatus.SHIPPED, "KITCHEN", "30.00"),
                orderOf(OrderStatus.DELIVERED, "KITCHEN", "40.00"),
                orderOf(OrderStatus.CANCELLED, "KITCHEN", "50.00"),
                orderOf(OrderStatus.REFUNDED, "KITCHEN", "60.00"));
        when(orderRepository.findByCreatedAtBetween(any(), any())).thenReturn(orders);

        SalesSummaryResponse summary = reportService.salesSummary(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertThat(summary.orderCount()).isEqualTo(6);
        assertThat(summary.revenue()).isEqualByComparingTo("90.00");
    }

    @Test
    void revenueByCategorySumsItemsInsteadOfThrowingOnDuplicateCategories() {
        List<Order> orders = List.of(
                orderOf(OrderStatus.PAID, "KITCHEN", "20.00"),
                orderOf(OrderStatus.PAID, "KITCHEN", "15.00"),
                orderOf(OrderStatus.DELIVERED, "GARDEN", "9.00"));
        when(orderRepository.findByCreatedAtBetween(any(), any())).thenReturn(orders);

        SalesSummaryResponse summary = reportService.salesSummary(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertThat(summary.revenueByCategory().get("KITCHEN")).isEqualByComparingTo("35.00");
        assertThat(summary.revenueByCategory().get("GARDEN")).isEqualByComparingTo("9.00");
    }

    @Test
    void salesSummaryRangeIsInclusiveOfTheWholeToDay() {
        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        when(orderRepository.findByCreatedAtBetween(from.capture(), to.capture())).thenReturn(List.of());

        reportService.salesSummary(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertThat(from.getValue()).isEqualTo(LocalDate.of(2026, 1, 1).atStartOfDay());
        assertThat(to.getValue()).isEqualTo(LocalDateTime.of(LocalDate.of(2026, 1, 31), LocalTime.MAX));
    }

    @Test
    void topProductsDelegatesToTheRepositoryWithRevenueStatusesAndTheRequestedLimit() {
        TopProductResponse row = new TopProductResponse(7L, "KIT-3002", "Chef's knife", 5, new BigDecimal("150.00"));
        when(orderRepository.topProducts(any(), any(), any(), any())).thenReturn(List.of(row));

        List<TopProductResponse> result = reportService.topProducts(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), 3);

        assertThat(result).containsExactly(row);
        ArgumentCaptor<Collection<OrderStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).topProducts(statuses.capture(), any(), any(), pageable.capture());
        assertThat(statuses.getValue()).containsExactlyInAnyOrder(OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(3);
    }
}
