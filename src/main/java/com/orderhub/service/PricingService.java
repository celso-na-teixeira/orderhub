package com.orderhub.service;

import com.orderhub.domain.Discount;
import com.orderhub.domain.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Transactional
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
        BigDecimal subtotal = order.calculateSubtotal();

        String appliedCode = order.getDiscountCode();
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (discountCode != null && !discountCode.isBlank()) {
            // a code that is already on the order has been counted once and is not counted again (BR-25)
            boolean alreadyApplied = discountCode.trim().equalsIgnoreCase(order.getDiscountCode());
            Discount discount = alreadyApplied
                    ? discountService.findAlreadyApplied(discountCode, subtotal)
                    : discountService.findApplicable(discountCode, subtotal);
            discountAmount = discountCalculator.calculate(discount, subtotal);
            if (!alreadyApplied) {
                discountService.markUsed(discount);
            }
            appliedCode = discount.getCode();
        }

        order.applyPricing(appliedCode, discountAmount);
    }
}
