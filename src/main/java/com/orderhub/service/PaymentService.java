package com.orderhub.service;

import com.orderhub.security.OrderAccessPolicy;
import com.orderhub.domain.Order;
import com.orderhub.domain.OrderStatus;
import com.orderhub.domain.Payment;
import com.orderhub.domain.PaymentStatus;
import com.orderhub.dto.PaymentRequest;
import com.orderhub.dto.PaymentResponse;
import com.orderhub.exception.BusinessRuleException;
import com.orderhub.exception.PaymentDeclinedException;
import com.orderhub.exception.ResourceNotFoundException;
import com.orderhub.mapper.OrderMapper;
import com.orderhub.repository.OrderRepository;
import com.orderhub.repository.PaymentRepository;
import com.orderhub.security.AppUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final InventoryService inventoryService;
    private final ReservationService reservationService;
    private final AuditService auditService;
    private final OrderMapper orderMapper;
    private final OrderAccessPolicy orderAccessPolicy;

    public PaymentService(OrderRepository orderRepository,
                          PaymentRepository paymentRepository,
                          PaymentGateway paymentGateway,
                          InventoryService inventoryService,
                          ReservationService reservationService,
                          AuditService auditService,
                          OrderMapper orderMapper,
                          OrderAccessPolicy orderAccessPolicy) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.inventoryService = inventoryService;
        this.reservationService = reservationService;
        this.auditService = auditService;
        this.orderMapper = orderMapper;
        this.orderAccessPolicy = orderAccessPolicy;
    }

    /**
     * The payment record, the stock commitment and the status change are written in one transaction (BR-43).
     * A declined charge is the one exception: its FAILED payment record is kept and the order stays as it was.
     */
    @Transactional(noRollbackFor = PaymentDeclinedException.class)
    public PaymentResponse pay(Long orderId, PaymentRequest request, AppUserDetails currentUser) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        orderAccessPolicy.assertCanAccess(order, currentUser);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessRuleException("Order " + order.getOrderNumber() + " cannot be paid while " + order.getStatus());
        }
        if (!request.amount().equals(order.getTotal())) {
            throw new BusinessRuleException("Payment amount " + request.amount()
                    + " does not match order total " + order.getTotal());
        }

        log.info("Charging {} for order {}", request.amount(), order.getOrderNumber());
        PaymentGateway.GatewayResult result =
                paymentGateway.charge(order.getOrderNumber(), request.amount(), request.cardToken());

        Payment payment = new Payment(order, request.amount(),
                result.success() ? PaymentStatus.SUCCEEDED : PaymentStatus.FAILED,
                result.reference());
        paymentRepository.save(payment);

        if (!result.success()) {
            throw new PaymentDeclinedException("Payment declined: " + result.message());
        }

        completeOrder(order);
        auditService.record("ORDER", order.getId(), "PAID", currentUser.getUsername(), order);
        return orderMapper.toPaymentResponse(payment);
    }

    private void completeOrder(Order order) {
        order.getItems().forEach(item ->
                inventoryService.commit(item.getProduct().getId(), item.getQuantity()));
        reservationService.markConverted(order.getId());
        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);
        log.info("Order {} marked as PAID", order.getOrderNumber());
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> findForOrder(Long orderId, AppUserDetails currentUser) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        orderAccessPolicy.assertCanAccess(order, currentUser);
        return paymentRepository.findByOrderId(orderId).stream()
                .map(orderMapper::toPaymentResponse)
                .toList();
    }
}
