package com.orderhub.service;

import com.orderhub.domain.Customer;
import com.orderhub.domain.Order;
import com.orderhub.domain.OrderStatus;
import com.orderhub.domain.Role;
import com.orderhub.domain.User;
import com.orderhub.dto.PaymentRequest;
import com.orderhub.exception.BusinessRuleException;
import com.orderhub.mapper.OrderMapper;
import com.orderhub.repository.CustomerRepository;
import com.orderhub.repository.OrderRepository;
import com.orderhub.repository.PaymentRepository;
import com.orderhub.security.AppUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private CustomerRepository customerRepository;
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
        order.setSubtotal(new BigDecimal("59.90"));
        order.setTotal(new BigDecimal("59.90"));
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

    @Test
    void rejectsPaymentForAlreadyPaidOrder() {
        order.setStatus(OrderStatus.PAID);
        PaymentRequest request = new PaymentRequest(new BigDecimal("59.90"), "tok_visa");

        assertThatThrownBy(() -> paymentService.pay(3L, request, staff))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsPaymentForCancelledOrder() {
        order.setStatus(OrderStatus.CANCELLED);
        PaymentRequest request = new PaymentRequest(new BigDecimal("59.90"), "tok_visa");

        assertThatThrownBy(() -> paymentService.pay(3L, request, staff))
                .isInstanceOf(BusinessRuleException.class);
        verify(paymentGateway, never()).charge(anyString(), any(), anyString());
    }
}
