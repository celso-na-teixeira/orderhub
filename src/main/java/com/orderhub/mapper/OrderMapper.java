package com.orderhub.mapper;

import com.orderhub.domain.Order;
import com.orderhub.domain.OrderItem;
import com.orderhub.domain.Payment;
import com.orderhub.dto.OrderItemResponse;
import com.orderhub.dto.OrderResponse;
import com.orderhub.dto.PaymentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Mapper
public interface OrderMapper {

    @Mapping(target = "customerId", source = "customer.id")
    OrderResponse toResponse(Order order);

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "sku", source = "product.sku")
    @Mapping(target = "productName", source = "product.name")
    OrderItemResponse toItemResponse(OrderItem item);

    @Mapping(target = "orderId", source = "order.id")
    PaymentResponse toPaymentResponse(Payment payment);

    // picked up by toResponse for the order lines, so they always come back in SKU order
    default List<OrderItemResponse> toItemResponses(Set<OrderItem> items) {
        return items.stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getSku()))
                .map(this::toItemResponse)
                .toList();
    }
}
