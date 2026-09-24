package com.orderhub.mapper;

import com.orderhub.domain.Order;
import com.orderhub.domain.OrderItem;
import com.orderhub.domain.Payment;
import com.orderhub.dto.OrderItemResponse;
import com.orderhub.dto.OrderResponse;
import com.orderhub.dto.PaymentResponse;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class OrderMapper {

    public OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getSku()))
                .map(this::toItemResponse)
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getCustomer().getId(),
                order.getStatus(),
                items,
                order.getSubtotal(),
                order.getDiscountAmount(),
                order.getTotal(),
                order.getDiscountCode(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    public OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(
                item.getProduct().getId(),
                item.getProduct().getSku(),
                item.getProduct().getName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getLineTotal());
    }

    public PaymentResponse toPaymentResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getProviderReference(),
                payment.getCreatedAt());
    }
}
