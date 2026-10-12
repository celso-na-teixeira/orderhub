package com.orderhub.service;

import com.orderhub.domain.Customer;
import com.orderhub.domain.Order;
import com.orderhub.domain.OrderItem;
import com.orderhub.domain.OrderStatus;
import com.orderhub.domain.Product;
import com.orderhub.domain.Payment;
import com.orderhub.domain.PaymentStatus;
import com.orderhub.domain.Role;
import com.orderhub.domain.User;
import com.orderhub.dto.PaymentRequest;
import com.orderhub.exception.BusinessRuleException;
import com.orderhub.mapper.OrderMapper;
import com.orderhub.repository.OrderRepository;
import com.orderhub.repository.PaymentRepository;
import com.orderhub.security.AppUserDetails;
import com.orderhub.security.OrderAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentGateway paymentGateway;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private ReservationService reservationService;
    @Mock
    private AuditService auditService;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderAccessPolicy orderAccessPolicy;

    @InjectMocks
    private PaymentService paymentService;

    private AppUserDetails staff;
    private Order order;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(2L);
        user.setEmail("staff@orderhub.io");
        user.setRole(Role.STAFF);
        staff = new AppUserDetails(user);

        order = new Order();
        order.setOrderNumber("ORD-20260910-00001");
        order.setCustomer(new Customer(null, "Carol", "carol@example.com"));
        order.setStatus(OrderStatus.PENDING);
        order.addItem(new OrderItem(new Product("TST-0001", "Test product", "TEST", new BigDecimal("1.00")), 1, new BigDecimal("59.90")));
        order.applyPricing(null, BigDecimal.ZERO);
        when(orderRepository.findById(3L)).thenReturn(Optional.of(order));

        lenient().when(paymentGateway.charge(anyString(), any(), anyString()))
                .thenReturn(new PaymentGateway.GatewayResult(true, "psp_test", "Approved"));
    }

    @Test
    void rejectsAmountThatDoesNotMatchTotal() {
        PaymentRequest request = new PaymentRequest(new BigDecimal("50.00"), "tok_visa");

        assertThatThrownBy(() -> paymentService.pay(3L, request, staff))
                .isInstanceOf(BusinessRuleException.class);
        verify(paymentGateway, never()).charge(anyString(), any(), anyString());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
    void rejectsPaymentForOrdersThatAreNotPending(OrderStatus status) {
        order.setStatus(status);
        PaymentRequest request = new PaymentRequest(new BigDecimal("59.90"), "tok_visa");

        assertThatThrownBy(() -> paymentService.pay(3L, request, staff))
            .isInstanceOf(BusinessRuleException.class);
        verify(paymentGateway, never()).charge(anyString(), any(), anyString());

    }

    @Test 
    void paysPendingOrder(){
        PaymentRequest request = new PaymentRequest(new BigDecimal("59.90"), "tok_visa");

        paymentService.pay(3L, request, staff);

        verify(paymentGateway, times(1)).charge(eq(order.getOrderNumber()), eq(new BigDecimal("59.90")), eq("tok_visa"));

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("59.90");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);

    }

    @Test
    void declinedCardLeavesOrderPendingAndRecordsFailedPayment() {
        when(paymentGateway.charge(anyString(), any(), anyString()))
                .thenReturn(new PaymentGateway.GatewayResult(false, null, "Card declined"));
        PaymentRequest request = new PaymentRequest(new BigDecimal("59.90"), "tok_decline");

        assertThatThrownBy(() -> paymentService.pay(3L, request, staff))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("declined");

        // Assert side effects
        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(inventoryService, never()).commit(any(), anyInt());
    }
}
