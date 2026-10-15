package com.orderhub.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.orderhub.domain.Customer;
import com.orderhub.domain.Order;
import com.orderhub.domain.OrderItem;
import com.orderhub.domain.OrderStatus;
import com.orderhub.domain.Payment;
import com.orderhub.domain.PaymentStatus;
import com.orderhub.domain.Product;
import com.orderhub.exception.BusinessRuleException;
import com.orderhub.exception.InvalidOrderStateException;
import com.orderhub.repository.OrderRepository;
import com.orderhub.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private AuditService auditService;

    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks
    private RefundService refundService;

    private Order order;

    @BeforeEach
    void setUp() {
        order = new Order();
        order.setOrderNumber("ORD-20260910-00001");
        order.setCustomer(new Customer(null, "Carol", "carol@example.com"));
        order.addItem(new OrderItem(new Product("TST-0001", "Test product", "TEST", new BigDecimal("59.90")),
                2, new BigDecimal("59.90")));
        order.applyPricing(null, BigDecimal.ZERO);
        when(orderRepository.findById(3L)).thenReturn(Optional.of(order));
    }

    @Test
    void refundingPaidOrderPutsStockBackAndRecordsRefund() {
        order.setStatus(OrderStatus.PAID);

        refundService.refund(3L, "changed mind");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        verify(inventoryService).restock(any(), eq(2));
        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(meterRegistry.counter("orderhub.orders.refunded").count()).isEqualTo(1);
    }

    @Test
    void refundingDeliveredOrderWithinWindowDoesNotRestock() {
        order.markDelivered(LocalDateTime.now(ZoneOffset.UTC).minusDays(29));

        refundService.refund(3L, "damaged");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        verify(inventoryService, never()).restock(any(), anyInt());
    }

    @Test
    void refundingDeliveredOrderAfterThirtyDaysIsRejected() {
        order.markDelivered(LocalDateTime.now(ZoneOffset.UTC).minusDays(31));

        assertThatThrownBy(() -> refundService.refund(3L, "too late"))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void refundingDeliveredOrderWithoutDeliveryDateIsRejected() {
        order.setStatus(OrderStatus.DELIVERED);

        assertThatThrownBy(() -> refundService.refund(3L, "unknown"))
                .isInstanceOf(BusinessRuleException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void shippedOrderCannotBeRefunded() {
        order.setStatus(OrderStatus.SHIPPED);

        assertThatThrownBy(() -> refundService.refund(3L, "in transit"))
                .isInstanceOf(InvalidOrderStateException.class);
        verify(inventoryService, never()).restock(any(), anyInt());
    }
}
