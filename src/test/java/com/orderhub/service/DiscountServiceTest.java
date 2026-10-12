package com.orderhub.service;

import com.orderhub.domain.Discount;
import com.orderhub.domain.DiscountType;
import com.orderhub.dto.DiscountRequest;
import com.orderhub.exception.BusinessRuleException;
import com.orderhub.exception.ConflictException;
import com.orderhub.repository.DiscountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscountServiceTest {

    private static final BigDecimal SUBTOTAL = new BigDecimal("100.00");

    @Mock
    private DiscountRepository discountRepository;
    @Mock
    private DiscountCalculator discountCalculator;

    @InjectMocks
    private DiscountService discountService;

    @Test
    void codeBelowItsUsageLimitIsApplicable() {
        Discount discount = discount("LIMITED20", 3, 2);
        when(discountRepository.findByCodeIgnoreCase("LIMITED20")).thenReturn(Optional.of(discount));

        assertThat(discountService.findApplicable("LIMITED20", SUBTOTAL)).isSameAs(discount);
    }

    @Test
    void codeAtItsUsageLimitIsRejected() {
        when(discountRepository.findByCodeIgnoreCase("LIMITED20")).thenReturn(Optional.of(discount("LIMITED20", 3, 3)));

        assertThatThrownBy(() -> discountService.findApplicable("LIMITED20", SUBTOTAL))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("usage limit");
    }

    @Test
    void codeAlreadyOnAnOrderIsNotCheckedAgainstTheUsageLimit() {
        Discount discount = discount("LIMITED20", 3, 3);
        when(discountRepository.findByCodeIgnoreCase("LIMITED20")).thenReturn(Optional.of(discount));

        assertThat(discountService.findAlreadyApplied("LIMITED20", SUBTOTAL)).isSameAs(discount);
    }

    @Test
    void markUsedFailsWhenNoUsesAreLeft() {
        when(discountRepository.incrementUsage(any())).thenReturn(0);

        assertThatThrownBy(() -> discountService.markUsed(discount("LIMITED20", 3, 3)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("usage limit");
    }

    @Test
    void createRejectsACodeThatAlreadyExistsIgnoringCase() {
        when(discountRepository.existsByCodeIgnoreCase("SPRING5")).thenReturn(true);

        assertThatThrownBy(() -> discountService.create(request(" spring5 ", DiscountType.FIXED_AMOUNT, "5.00")))
                .isInstanceOf(ConflictException.class);
        verify(discountRepository, never()).save(any());
    }

    @Test
    void createRejectsAPercentageAboveOneHundred() {
        assertThatThrownBy(() -> discountService.create(request("HUGE", DiscountType.PERCENTAGE, "150")))
                .isInstanceOf(BusinessRuleException.class);
        verify(discountRepository, never()).save(any());
    }

    private static Discount discount(String code, Integer maxUses, int usedCount) {
        Discount discount = new Discount();
        discount.setCode(code);
        discount.setType(DiscountType.PERCENTAGE);
        discount.setValue(new BigDecimal("20"));
        discount.setMaxUses(maxUses);
        discount.setUsedCount(usedCount);
        return discount;
    }

    private static DiscountRequest request(String code, DiscountType type, String value) {
        return new DiscountRequest(code, null, type, new BigDecimal(value), null, null, null, null, null);
    }
}
