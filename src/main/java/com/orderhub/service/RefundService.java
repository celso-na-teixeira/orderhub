package com.orderhub.service;

import com.orderhub.domain.Order;
import com.orderhub.domain.OrderStatus;
import com.orderhub.exception.InvalidOrderStateException;
import com.orderhub.exception.ResourceNotFoundException;
import com.orderhub.repository.OrderRepository;
import com.orderhub.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final OrderRepository orderRepository;
    private final AuditService auditService;

    public RefundService(OrderRepository orderRepository, AuditService auditService) {
        this.orderRepository = orderRepository;
        this.auditService = auditService;
    }

    @Transactional
    public Order refund(Long orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (!order.getStatus().canTransitionTo(OrderStatus.REFUNDED)) {
            throw new InvalidOrderStateException(orderId, order.getStatus(), OrderStatus.REFUNDED);
        }

        order.setStatus(OrderStatus.REFUNDED);
        log.info("Order {} refunded. Reason: {}", order.getOrderNumber(), reason);
        auditService.record("ORDER", orderId, "REFUNDED", CurrentUser.emailOrSystem(), reason);
        return order;
    }
}
