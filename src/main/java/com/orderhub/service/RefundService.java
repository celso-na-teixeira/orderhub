package com.orderhub.service;

import com.orderhub.domain.Order;
import com.orderhub.domain.OrderStatus;
import com.orderhub.domain.Payment;
import com.orderhub.domain.PaymentStatus;
import com.orderhub.exception.BusinessRuleException;
import com.orderhub.exception.InvalidOrderStateException;
import com.orderhub.exception.ResourceNotFoundException;
import com.orderhub.repository.OrderRepository;
import com.orderhub.repository.PaymentRepository;
import com.orderhub.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class RefundService {

    /** How long after delivery a delivered order can still be refunded (BR-60). */
    static final Duration REFUND_WINDOW = Duration.ofDays(30);

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final InventoryService inventoryService;
    private final AuditService auditService;

    public Order refund(Long orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (!order.getStatus().canTransitionTo(OrderStatus.REFUNDED)) {
            throw new InvalidOrderStateException(orderId, order.getStatus(), OrderStatus.REFUNDED);
        }

        if (order.getStatus() == OrderStatus.DELIVERED) {
            assertWithinRefundWindow(order);
        }
        if (order.getStatus() == OrderStatus.PAID) {
            // nothing has shipped, so the committed stock goes straight back on hand (BR-62);
            // delivered goods come back through inspection instead
            order.getItems().stream()
                    .sorted(Comparator.comparing(item -> item.getProduct().getId()))
                    .forEach(item -> inventoryService.restock(item.getProduct().getId(), item.getQuantity()));
        }

        order.setStatus(OrderStatus.REFUNDED);
        // the original charge is left untouched; the refund is a payment record of its own (BR-61)
        paymentRepository.save(new Payment(order, order.getTotal(), PaymentStatus.REFUNDED, null));
        log.info("Order {} refunded. Reason: {}", order.getOrderNumber(), reason);
        auditService.record("ORDER", orderId, "REFUNDED", CurrentUser.emailOrSystem(), reason);
        return order;
    }

    private static void assertWithinRefundWindow(Order order) {
        LocalDateTime deliveredAt = order.getDeliveredAt();
        if (deliveredAt == null) {
            throw new BusinessRuleException("Order " + order.getOrderNumber() + " has no delivery date to refund against");
        }
        if (LocalDateTime.now(ZoneOffset.UTC).isAfter(deliveredAt.plus(REFUND_WINDOW))) {
            throw new BusinessRuleException("Order " + order.getOrderNumber()
                    + " was delivered more than " + REFUND_WINDOW.toDays() + " days ago and can no longer be refunded");
        }
    }
}
