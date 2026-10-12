package com.orderhub.service;

import com.orderhub.domain.Discount;
import com.orderhub.domain.DiscountType;
import com.orderhub.domain.Order;
import com.orderhub.domain.OrderItem;
import com.orderhub.domain.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

    @Mock
    private DiscountService discountService;
    @Mock
    private DiscountCalculator discountCalculator;

    @InjectMocks
    private PricingService pricingService;

    private Discount discount;

    @BeforeEach
    void setUp() {
        discount = new Discount();
        discount.setCode("FLAT20");
        discount.setType(DiscountType.FIXED_AMOUNT);
        discount.setValue(new BigDecimal("20.00"));
        when(discountCalculator.calculate(any(), any())).thenReturn(new BigDecimal("20.00"));
    }

    @Test
    void applyingACodeCountsOneUse() {
        Order order = orderWithLine("59.90");
        when(discountService.findApplicable("FLAT20", new BigDecimal("59.90"))).thenReturn(discount);

        pricingService.price(order, "FLAT20");

        assertThat(order.getDiscountCode()).isEqualTo("FLAT20");
        assertThat(order.getTotal()).isEqualByComparingTo("39.90");
        verify(discountService).markUsed(discount);
    }

    @Test
    void repricingWithTheCodeAlreadyOnTheOrderDoesNotCountASecondUse() {
        Order order = orderWithLine("59.90");
        when(discountService.findApplicable("FLAT20", new BigDecimal("59.90"))).thenReturn(discount);
        pricingService.price(order, "FLAT20");

        order.replaceItems(List.of(new OrderItem(new Product("TST-0001", "Test product", "TEST", new BigDecimal("1.00")), 1, new BigDecimal("89.00"))));
        when(discountService.findAlreadyApplied("FLAT20", new BigDecimal("89.00"))).thenReturn(discount);
        pricingService.price(order, order.getDiscountCode());

        assertThat(order.getTotal()).isEqualByComparingTo("69.00");
        verify(discountService, times(1)).markUsed(discount);
    }

    private static Order orderWithLine(String price) {
        Order order = new Order();
        order.addItem(new OrderItem(new Product("TST-0001", "Test product", "TEST", new BigDecimal("1.00")), 1, new BigDecimal(price)));
        return order;
    }
}
