package com.orderhub.service;

import com.orderhub.domain.Order;
import com.orderhub.domain.OrderStatus;
import com.orderhub.domain.Payment;
import com.orderhub.domain.PaymentStatus;
import com.orderhub.exception.InvalidOrderStateException;
import com.orderhub.exception.ResourceNotFoundException;
import com.orderhub.repository.OrderRepository;
import com.orderhub.repository.PaymentRepository;
import com.orderhub.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final AuditService auditService;

    public RefundService(OrderRepository orderRepository, PaymentRepository paymentRepository, AuditService auditService) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.auditService = auditService;
    }

    public Order refund(Long orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (!order.getStatus().canTransitionTo(OrderStatus.REFUNDED)) {
            throw new InvalidOrderStateException(orderId, order.getStatus(), OrderStatus.REFUNDED);
        }

        order.setStatus(OrderStatus.REFUNDED);
        // the original charge is left untouched; the refund is a payment record of its own (BR-61)
        paymentRepository.save(new Payment(order, order.getTotal(), PaymentStatus.REFUNDED, null));
        log.info("Order {} refunded. Reason: {}", order.getOrderNumber(), reason);
        auditService.record("ORDER", orderId, "REFUNDED", CurrentUser.emailOrSystem(), reason);
        return order;
    }
}
