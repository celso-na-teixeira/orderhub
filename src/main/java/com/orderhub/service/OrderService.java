package com.orderhub.service;

import com.orderhub.security.OrderAccessPolicy;
import com.orderhub.domain.Customer;
import com.orderhub.domain.Order;
import com.orderhub.domain.OrderItem;
import com.orderhub.domain.OrderStatus;
import com.orderhub.domain.Product;
import com.orderhub.dto.AuditEventResponse;
import com.orderhub.dto.CreateOrderRequest;
import com.orderhub.dto.OrderLineRequest;
import com.orderhub.exception.BusinessRuleException;
import com.orderhub.exception.InvalidOrderStateException;
import com.orderhub.exception.ResourceNotFoundException;
import com.orderhub.repository.CustomerRepository;
import com.orderhub.repository.OrderRepository;
import com.orderhub.security.AppUserDetails;
import com.orderhub.util.OrderNumberGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductService productService;
    private final InventoryService inventoryService;
    private final ReservationService reservationService;
    private final PricingService pricingService;
    private final AuditService auditService;
    private final OrderNumberGenerator orderNumberGenerator;
    private final OrderAccessPolicy orderAccessPolicy;

    public Order placeOrder(Long userId, CreateOrderRequest request, String idempotencyKey) {
        boolean idempotent = idempotencyKey != null && !idempotencyKey.isBlank();

        // with a key, the customer row is locked so that a repeated request waits for the first one to finish (BR-24)
        Customer customer = (idempotent
                ? customerRepository.findByUserIdForUpdate(userId)
                : customerRepository.findByUserId(userId))
                .orElseThrow(() -> new BusinessRuleException("Current user has no customer profile"));

        if (idempotent) {
            Optional<Order> existing = orderRepository.findByCustomerIdAndIdempotencyKey(customer.getId(), idempotencyKey);
            if (existing.isPresent()) {
                log.info("Idempotent replay for key {}, returning order {}", idempotencyKey, existing.get().getOrderNumber());
                return existing.get();
            }
        }

        Order order = new Order();
        order.setOrderNumber(orderNumberGenerator.next());
        order.setCustomer(customer);
        order.setStatus(OrderStatus.PENDING);
        order.setIdempotencyKey(idempotent ? idempotencyKey : null);

        Map<Long, Integer> quantities = mergeLines(request.items());
        for (Map.Entry<Long, Integer> line : quantities.entrySet()) {
            Product product = productService.getProduct(line.getKey());
            if (!product.isActive()) {
                throw new BusinessRuleException("Product " + product.getSku() + " is no longer available");
            }
            order.addItem(new OrderItem(product, line.getValue(), product.getPrice()));
        }

        pricingService.price(order, request.discountCode());
        Order saved = orderRepository.save(order);

        for (Map.Entry<Long, Integer> line : quantities.entrySet()) {
            inventoryService.reserve(line.getKey(), line.getValue());
            reservationService.create(saved.getId(), line.getKey(), line.getValue());
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
    public Page<Order> listOrders(Long customerId, OrderStatus status, Pageable pageable, AppUserDetails currentUser) {
        Long effectiveCustomerId = customerId;
        // customers only ever see their own orders (BR-28)
        if (currentUser.isCustomer()) {
            Long ownCustomerId = customerIdForUser(currentUser.getId());
            if (customerId != null && !customerId.equals(ownCustomerId)) {
                throw new AccessDeniedException("You can only list your own orders");
            }
            effectiveCustomerId = ownCustomerId;
        }
        if (effectiveCustomerId != null && status != null) {
            return orderRepository.findByCustomerIdAndStatus(effectiveCustomerId, status, pageable);
        }
        if (effectiveCustomerId != null) {
            return orderRepository.findWithItemsByCustomerId(effectiveCustomerId, pageable);
        }
        return orderRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<AuditEventResponse> historyFor(Long orderId, AppUserDetails currentUser) {
        // staff can still read the history of an order that has since been deleted
        if (currentUser.isCustomer()) {
            getOrder(orderId, currentUser);
        }
        return auditService.historyFor("ORDER", orderId);
    }

    private Long customerIdForUser(Long userId) {
        return customerRepository.findByUserId(userId)
                .map(Customer::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer for user", userId));
    }

    public Order cancelOrder(Long orderId, AppUserDetails currentUser) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        orderAccessPolicy.assertCanAccess(order, currentUser);
        if (!order.getStatus().canTransitionTo(OrderStatus.CANCELLED)) {
            throw new InvalidOrderStateException(orderId, order.getStatus(), OrderStatus.CANCELLED);
        }

        // a pending order only holds a reservation; once paid, the stock was committed and goes back on hand (BR-13)
        boolean pending = order.getStatus() == OrderStatus.PENDING;
        for (OrderItem item : inProductOrder(order)) {
            if (pending) {
                inventoryService.release(item.getProduct().getId(), item.getQuantity());
            } else {
                inventoryService.restock(item.getProduct().getId(), item.getQuantity());
            }
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
        orderAccessPolicy.assertCanAccess(order, currentUser);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidOrderStateException("Order " + order.getOrderNumber() + " is not pending");
        }

        Map<Long, Integer> oldQuantities = order.getItems().stream()
                .collect(Collectors.toMap(item -> item.getProduct().getId(), OrderItem::getQuantity));
        Map<Long, Integer> newQuantities = mergeLines(lines);

        List<OrderItem> newItems = new ArrayList<>();
        for (Map.Entry<Long, Integer> line : newQuantities.entrySet()) {
            Product product = productService.getProduct(line.getKey());
            if (!product.isActive()) {
                throw new BusinessRuleException("Product " + product.getSku() + " is no longer available");
            }
            newItems.add(new OrderItem(product, line.getValue(), product.getPrice()));
        }

        // Adjust stock by the difference only, locking rows in product-ID order (same rule as placeOrder/cancelOrder).
        Set<Long> touchedProducts = new TreeSet<>();
        touchedProducts.addAll(oldQuantities.keySet());
        touchedProducts.addAll(newQuantities.keySet());
        for (Long productId : touchedProducts) {
            int oldQty = oldQuantities.getOrDefault(productId, 0);
            int newQty = newQuantities.getOrDefault(productId, 0);
            if (newQty > oldQty) {
                inventoryService.reserve(productId, newQty - oldQty);
            } else if (oldQty > newQty) {
                inventoryService.release(productId, oldQty - newQty);
            }
        }

        order.replaceItems(newItems);
        pricingService.price(order, order.getDiscountCode());

        // The old per-line Reservation rows no longer match the order's items; replace them with fresh ones.
        reservationService.releaseForOrder(orderId);
        for (Map.Entry<Long, Integer> line : newQuantities.entrySet()) {
            reservationService.create(orderId, line.getKey(), line.getValue());
        }

        auditService.record("ORDER", orderId, "ITEMS_REPLACED", currentUser.getUsername(), order);
        return order;
    }

    public void deleteOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        orderRepository.delete(order);
        log.warn("Order {} deleted", order.getOrderNumber());
    }

    /**
     * Adds up lines for the same product, so each product appears once (BR-21).
     * The result is ordered by product id, which keeps stock rows locked in the same order for every request.
     */
    private static Map<Long, Integer> mergeLines(List<OrderLineRequest> lines) {
        Map<Long, Integer> quantities = new TreeMap<>();
        for (OrderLineRequest line : lines) {
            quantities.merge(line.productId(), line.quantity(), Integer::sum);
        }
        return quantities;
    }

    private static List<OrderItem> inProductOrder(Order order) {
        return order.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getId()))
                .toList();
    }
}
