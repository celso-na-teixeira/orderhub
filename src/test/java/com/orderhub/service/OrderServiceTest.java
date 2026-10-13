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
import com.orderhub.exception.InvalidOrderStateException;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
    void placeOrderMergesLinesForTheSameProduct() {
        Customer customer = new Customer(user(3L, Role.CUSTOMER), "Alice", "alice@example.com");
        when(customerRepository.findByUserId(3L)).thenReturn(Optional.of(customer));
        when(orderNumberGenerator.next()).thenReturn("ORD-TEST-00002");
        when(productService.getProduct(1L)).thenReturn(product("ELE-1001", "24.99"));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderLineRequest(1L, 2), new OrderLineRequest(1L, 3)), null);

        Order result = orderService.placeOrder(3L, request, null);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().iterator().next().getQuantity()).isEqualTo(5);
        verify(inventoryService).reserve(1L, 5);
    }

    @Test
    void placeOrderReturnsExistingOrderForSameIdempotencyKey() {
        Order existing = new Order();
        existing.setOrderNumber("ORD-20260901-00001");
        Customer customer = new Customer(user(3L, Role.CUSTOMER), "Alice", "alice@example.com");
        when(customerRepository.findByUserIdForUpdate(3L)).thenReturn(Optional.of(customer));
        when(orderRepository.findByCustomerIdAndIdempotencyKey(any(), eq("key-1"))).thenReturn(Optional.of(existing));

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
    void cancelPaidOrderPutsStockBackOnHand() {
        Order order = new Order();
        order.setStatus(OrderStatus.PAID);
        order.addItem(new OrderItem(product("KIT-3002", "59.90"), 2, new BigDecimal("59.90")));
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        Order result = orderService.cancelOrder(10L, new AppUserDetails(user(2L, Role.STAFF)));

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryService).restock(any(), eq(2));
        verify(inventoryService, never()).release(any(), anyInt());
    }

    @Test
    void cancelIsRejectedWhenCallerMayNotAccessTheOrder() {
        Order order = new Order();
        order.setStatus(OrderStatus.PENDING);
        order.addItem(new OrderItem(product("KIT-3002", "59.90"), 2, new BigDecimal("59.90")));
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        AppUserDetails customer = new AppUserDetails(user(3L, Role.CUSTOMER));
        doThrow(new AccessDeniedException("Order does not belong to current customer"))
                .when(orderAccessPolicy).assertCanAccess(order, customer);

        assertThatThrownBy(() -> orderService.cancelOrder(10L, customer))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(inventoryService, never()).release(any(), anyInt());
    }

    @Test
    void shippedOrderCannotBeCancelled() {
        Order order = new Order();
        order.setStatus(OrderStatus.SHIPPED);
        order.addItem(new OrderItem(product("KIT-3002", "59.90"), 2, new BigDecimal("59.90")));
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(10L, new AppUserDetails(user(2L, Role.STAFF))))
                .isInstanceOf(InvalidOrderStateException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        verify(inventoryService, never()).restock(any(), anyInt());
        verify(inventoryService, never()).release(any(), anyInt());
    }

    @Test
    void cancelledOrderCannotBeCancelledAgain() {
        Order order = new Order();
        order.setStatus(OrderStatus.CANCELLED);
        order.addItem(new OrderItem(product("KIT-3002", "59.90"), 2, new BigDecimal("59.90")));
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(10L, new AppUserDetails(user(2L, Role.STAFF))))
                .isInstanceOf(InvalidOrderStateException.class);
        verify(inventoryService, never()).restock(any(), anyInt());
        verify(inventoryService, never()).release(any(), anyInt());
    }

    @Test
    void cancelThrowsResourceNotFoundWhenOrderDoesNotExist() {
        when(orderRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancelOrder(10L, new AppUserDetails(user(2L, Role.STAFF))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void replaceItemsIsRejectedWhenCallerMayNotAccessTheOrder() {
        Order order = new Order();
        order.setStatus(OrderStatus.PENDING);
        order.addItem(new OrderItem(product("KIT-3002", "59.90"), 2, new BigDecimal("59.90")));
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        AppUserDetails customer = new AppUserDetails(user(3L, Role.CUSTOMER));
        doThrow(new AccessDeniedException("Order does not belong to current customer"))
                .when(orderAccessPolicy).assertCanAccess(order, customer);

        assertThatThrownBy(() -> orderService.replaceItems(10L, List.of(new OrderLineRequest(1L, 1)), customer))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(order.getItems()).hasSize(1);
        verify(inventoryService, never()).reserve(anyLong(), anyInt());
        verify(inventoryService, never()).release(any(), anyInt());
    }

    @Test
    void customerListsOnlyTheirOwnOrders() {
        Customer customer = customerWithId(7L);
        when(customerRepository.findByUserId(3L)).thenReturn(Optional.of(customer));
        Pageable pageable = PageRequest.of(0, 20);

        orderService.listOrders(null, null, pageable, new AppUserDetails(user(3L, Role.CUSTOMER)));

        verify(orderRepository).findWithItemsByCustomerId(7L, pageable);
        verify(orderRepository, never()).findAll(pageable);
    }

    @Test
    void customerCannotListAnotherCustomersOrders() {
        Customer customer = customerWithId(7L);
        when(customerRepository.findByUserId(3L)).thenReturn(Optional.of(customer));
        Pageable pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> orderService.listOrders(8L, null, pageable, new AppUserDetails(user(3L, Role.CUSTOMER))))
                .isInstanceOf(AccessDeniedException.class);
        verify(orderRepository, never()).findWithItemsByCustomerId(anyLong(), any());
    }

    @Test
    void staffCanListAnyCustomersOrders() {
        Pageable pageable = PageRequest.of(0, 20);

        orderService.listOrders(8L, null, pageable, new AppUserDetails(user(2L, Role.STAFF)));

        verify(orderRepository).findWithItemsByCustomerId(8L, pageable);
    }

    @Test
    void customerCannotReadHistoryOfAnOrderTheyMayNotAccess() {
        Order order = new Order();
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        AppUserDetails customer = new AppUserDetails(user(3L, Role.CUSTOMER));
        doThrow(new AccessDeniedException("Order does not belong to current customer"))
                .when(orderAccessPolicy).assertCanAccess(order, customer);

        assertThatThrownBy(() -> orderService.historyFor(10L, customer))
                .isInstanceOf(AccessDeniedException.class);
        verify(auditService, never()).historyFor(any(), anyLong());
    }

    @Test
    void staffReadHistoryWithoutAnOwnershipCheck() {
        orderService.historyFor(10L, new AppUserDetails(user(2L, Role.STAFF)));

        verify(auditService).historyFor("ORDER", 10L);
        verify(orderAccessPolicy, never()).assertCanAccess(any(), any());
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

    private static Customer customerWithId(Long id) {
        Customer customer = new Customer(user(3L, Role.CUSTOMER), "Alice", "alice@example.com");
        ReflectionTestUtils.setField(customer, "id", id);
        return customer;
    }

    private static Product product(String sku, String price) {
        return new Product(sku, sku, "TEST", new BigDecimal(price));
    }
}
