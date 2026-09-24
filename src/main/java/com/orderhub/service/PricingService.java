package com.orderhub.service;

import com.orderhub.domain.Discount;
import com.orderhub.domain.Order;
import com.orderhub.domain.OrderItem;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class PricingService {

    private final DiscountService discountService;
    private final DiscountCalculator discountCalculator;

    public PricingService(DiscountService discountService, DiscountCalculator discountCalculator) {
        this.discountService = discountService;
        this.discountCalculator = discountCalculator;
    }

    /**
     * Calculates subtotal, discount and total for the order and applies them to it.
     */
    public void price(Order order, String discountCode) {
        BigDecimal subtotal = order.getItems().stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal discountAmount = BigDecimal.ZERO;
        if (discountCode != null && !discountCode.isBlank()) {
            Discount discount = discountService.findApplicable(discountCode, subtotal);
            discountAmount = discountCalculator.calculate(discount, subtotal);
            discountService.markUsed(discount);
            order.setDiscountCode(discount.getCode());
        }

        order.setSubtotal(subtotal);
        order.setDiscountAmount(discountAmount);
        order.setTotal(subtotal.subtract(discountAmount));
    }
}
