package com.orderhub.service;

import com.orderhub.security.OrderAccessPolicy;
import com.orderhub.domain.Customer;
import com.orderhub.domain.Order;
import com.orderhub.domain.OrderItem;
import com.orderhub.domain.OrderStatus;
import com.orderhub.domain.Product;
import com.orderhub.dto.CreateOrderRequest;
import com.orderhub.dto.OrderLineRequest;
import com.orderhub.exception.BusinessRuleException;
import com.orderhub.exception.InvalidOrderStateException;
import com.orderhub.exception.ResourceNotFoundException;
import com.orderhub.repository.CustomerRepository;
import com.orderhub.repository.OrderRepository;
import com.orderhub.security.AppUserDetails;
import com.orderhub.util.OrderNumberGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductService productService;
    private final InventoryService inventoryService;
    private final ReservationService reservationService;
    private final PricingService pricingService;
    private final AuditService auditService;
    private final OrderNumberGenerator orderNumberGenerator;
    private final OrderAccessPolicy orderAccessPolicy;

    public OrderService(OrderRepository orderRepository,
                        CustomerRepository customerRepository,
                        ProductService productService,
                        InventoryService inventoryService,
                        ReservationService reservationService,
                        PricingService pricingService,
                        AuditService auditService,
                        OrderNumberGenerator orderNumberGenerator,
                        OrderAccessPolicy orderAccessPolicy) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.productService = productService;
        this.inventoryService = inventoryService;
        this.reservationService = reservationService;
        this.pricingService = pricingService;
        this.auditService = auditService;
        this.orderNumberGenerator = orderNumberGenerator;
        this.orderAccessPolicy = orderAccessPolicy;
    }

    public Order placeOrder(Long userId, CreateOrderRequest request, String idempotencyKey) {
        if (idempotencyKey != null) {
            Optional<Order> existing = orderRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Idempotent replay for key {}, returning order {}", idempotencyKey, existing.get().getOrderNumber());
                return existing.get();
            }
        }

        Customer customer = customerRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessRuleException("Current user has no customer profile"));

        Order order = new Order();
        order.setOrderNumber(orderNumberGenerator.next());
        order.setCustomer(customer);
        order.setStatus(OrderStatus.PENDING);
        order.setIdempotencyKey(idempotencyKey);

        for (OrderLineRequest line : request.items()) {
            Product product = productService.getProduct(line.productId());
            if (!product.isActive()) {
                throw new BusinessRuleException("Product " + product.getSku() + " is no longer available");
            }
            order.addItem(new OrderItem(product, line.quantity(), product.getPrice()));
        }

        pricingService.price(order, request.discountCode());
        Order saved = orderRepository.save(order);

        for (OrderLineRequest line : request.items()) {
            inventoryService.reserve(line.productId(), line.quantity());
            reservationService.create(saved.getId(), line.productId(), line.quantity());
        }

        auditService.record("ORDER", saved.getId(), "CREATED", customer.getEmail(), saved);
        log.info("Order {} placed by customer {} (total {})", saved.getOrderNumber(), customer.getId(), saved.getTotal());
        return saved;
    }

    @Transactional(readOnly = true)
    public Order getOrder(Long id, AppUserDetails currentUser) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        
        orderAccessPolicy.assertCanAccess(order, currentUser);

        return order;
    }

    @Transactional(readOnly = true)
    public Page<Order> listOrders(Long customerId, OrderStatus status, Pageable pageable) {
        if (customerId != null && status != null) {
            return orderRepository.findByCustomerIdAndStatus(customerId, status, pageable);
        }
        if (customerId != null) {
            return orderRepository.findWithItemsByCustomerId(customerId, pageable);
        }
        return orderRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Long customerIdForUser(Long userId) {
        return customerRepository.findByUserId(userId)
                .map(Customer::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer for user", userId));
    }

    public Order cancelOrder(Long orderId, AppUserDetails currentUser) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (currentUser.isCustomer() && !order.getCustomer().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You can only cancel your own orders");
        }
        if (!order.getStatus().canTransitionTo(OrderStatus.CANCELLED)) {
            throw new InvalidOrderStateException(orderId, order.getStatus(), OrderStatus.CANCELLED);
        }

        for (OrderItem item : order.getItems()) {
            inventoryService.release(item.getProduct().getId(), item.getQuantity());
        }
        reservationService.releaseForOrder(order.getId());

        order.setStatus(OrderStatus.CANCELLED);
        auditService.record("ORDER", orderId, "CANCELLED", currentUser.getUsername(), order);
        log.info("Order {} cancelled by {}", order.getOrderNumber(), currentUser.getUsername());
        return order;
    }

    public Order updateStatus(Long orderId, OrderStatus newStatus, AppUserDetails currentUser) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        OrderStatus previous = order.getStatus();
        if (!previous.canTransitionTo(newStatus)) {
            throw new InvalidOrderStateException(orderId, previous, newStatus);
        }
        order.setStatus(newStatus);
        auditService.record("ORDER", orderId, "STATUS_CHANGED", currentUser.getUsername(), order);
        log.info("Order {} moved {} -> {}", order.getOrderNumber(), previous, newStatus);
        return order;
    }

    public Order replaceItems(Long orderId, List<OrderLineRequest> lines, AppUserDetails currentUser) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        if (order.getStatus() == OrderStatus.SHIPPED) {
            throw new InvalidOrderStateException("Order " + order.getOrderNumber() + " has already shipped");
        }

        order.setItems(new HashSet<>());
        for (OrderLineRequest line : lines) {
            Product product = productService.getProduct(line.productId());
            order.addItem(new OrderItem(product, line.quantity(), product.getPrice()));
        }
        pricingService.price(order, order.getDiscountCode());

        auditService.record("ORDER", orderId, "ITEMS_REPLACED", currentUser.getUsername(), order);
        return order;
    }

    public void deleteOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        orderRepository.delete(order);
        log.warn("Order {} deleted", order.getOrderNumber());
    }
}
