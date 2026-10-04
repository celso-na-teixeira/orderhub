package com.orderhub.service;

import com.orderhub.domain.Customer;
import com.orderhub.domain.Order;
import com.orderhub.domain.OrderItem;
import com.orderhub.domain.OrderStatus;
import com.orderhub.domain.Product;
import com.orderhub.domain.Role;
import com.orderhub.domain.User;
import com.orderhub.dto.CreateOrderRequest;
import com.orderhub.dto.OrderLineRequest;
import com.orderhub.exception.ResourceNotFoundException;
import com.orderhub.repository.CustomerRepository;
import com.orderhub.repository.OrderRepository;
import com.orderhub.security.AppUserDetails;
import com.orderhub.security.OrderAccessPolicy;
import com.orderhub.util.OrderNumberGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private ProductService productService;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private ReservationService reservationService;
    @Mock
    private PricingService pricingService;
    @Mock
    private AuditService auditService;
    @Mock
    private OrderNumberGenerator orderNumberGenerator;
    @Mock
    private OrderAccessPolicy orderAccessPolicy;

    @InjectMocks
    private OrderService orderService;

    @Test
    void placeOrderCreatesPendingOrderAndReservesStock() {
        Customer customer = new Customer(user(3L, Role.CUSTOMER), "Alice", "alice@example.com");
        when(customerRepository.findByUserId(3L)).thenReturn(Optional.of(customer));
        when(orderNumberGenerator.next()).thenReturn("ORD-TEST-00001");
        when(productService.getProduct(1L)).thenReturn(product("ELE-1001", "24.99"));
        when(productService.getProduct(2L)).thenReturn(product("ELE-1002", "89.00"));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderLineRequest(1L, 2), new OrderLineRequest(2L, 1)), null);

        Order result = orderService.placeOrder(3L, request, null);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.getItems()).hasSize(2);
        verify(orderRepository).save(any());
        verify(inventoryService, times(2)).reserve(anyLong(), anyInt());
    }

    @Test
    void placeOrderReturnsExistingOrderForSameIdempotencyKey() {
        Order existing = new Order();
        existing.setOrderNumber("ORD-20260901-00001");
        when(orderRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderLineRequest(1L, 1)), null);

        Order result = orderService.placeOrder(3L, request, "key-1");

        assertThat(result).isSameAs(existing);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void cancelPendingOrderReleasesStock() {
        Order order = new Order();
        order.setStatus(OrderStatus.PENDING);
        order.addItem(new OrderItem(product("KIT-3002", "59.90"), 2, new BigDecimal("59.90")));
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        Order result = orderService.cancelOrder(10L, new AppUserDetails(user(2L, Role.STAFF)));

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryService).release(any(), eq(2));
        verify(reservationService).releaseForOrder(any());
    }

    @Test
    void returnsOrderWhenCallerIsAllowed() {
        Order order = new Order();
        order.setStatus(OrderStatus.PENDING);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        AppUserDetails staff = new AppUserDetails(user(2L, Role.STAFF));

        Order result = orderService.getOrder(10L, staff);

        assertThat(result).isSameAs(order);
        verify(orderAccessPolicy).assertCanAccess(order, staff);
    }

    @Test
    void throwsResourceNotFoundWhenOrderDoesNotExist() {
        when(orderRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(10L, new AppUserDetails(user(2L, Role.STAFF))))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(orderAccessPolicy, never()).assertCanAccess(any(), any());
    }

    private static User user(Long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setEmail("user" + id + "@example.com");
        user.setRole(role);
        return user;
    }

    private static Product product(String sku, String price) {
        Product product = new Product();
        product.setSku(sku);
        product.setName(sku);
        product.setPrice(new BigDecimal(price));
        product.setActive(true);
        return product;
    }
}
