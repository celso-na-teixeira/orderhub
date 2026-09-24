package com.orderhub.service;

import com.orderhub.domain.Discount;
import com.orderhub.dto.DiscountRequest;
import com.orderhub.dto.DiscountValidationResponse;
import com.orderhub.exception.BusinessRuleException;
import com.orderhub.exception.ResourceNotFoundException;
import com.orderhub.repository.DiscountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class DiscountService {

    private static final Logger log = LoggerFactory.getLogger(DiscountService.class);

    private final DiscountRepository discountRepository;
    private final DiscountCalculator discountCalculator;

    public DiscountService(DiscountRepository discountRepository, DiscountCalculator discountCalculator) {
        this.discountRepository = discountRepository;
        this.discountCalculator = discountCalculator;
    }

    /**
     * Resolves a discount code and verifies that it can be used for an order with the given subtotal.
     */
    @Transactional(readOnly = true)
    public Discount findApplicable(String code, BigDecimal subtotal) {
        Discount discount = discountRepository.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new BusinessRuleException("Unknown discount code: " + code));

        LocalDateTime now = LocalDateTime.now();
        if (!discount.isActive()) {
            throw new BusinessRuleException("Discount " + code + " is no longer active");
        }
        if (discount.getValidFrom() != null && now.isBefore(discount.getValidFrom())) {
            throw new BusinessRuleException("Discount " + code + " is not valid yet");
        }
        if (discount.getValidUntil() != null && now.isAfter(discount.getValidUntil())) {
            throw new BusinessRuleException("Discount " + code + " has expired");
        }
        if (discount.getMaxUses() != null && discount.getUsedCount() > discount.getMaxUses()) {
            throw new BusinessRuleException("Discount " + code + " has reached its usage limit");
        }
        if (discount.getMinOrderAmount() != null && subtotal.compareTo(discount.getMinOrderAmount()) < 0) {
            throw new BusinessRuleException("Order subtotal is below the minimum of " + discount.getMinOrderAmount()
                    + " required for " + code);
        }
        return discount;
    }

    public void markUsed(Discount discount) {
        discount.setUsedCount(discount.getUsedCount() + 1);
        log.debug("Discount {} used {} times", discount.getCode(), discount.getUsedCount());
    }

    @Transactional(readOnly = true)
    public DiscountValidationResponse validate(String code, BigDecimal subtotal) {
        try {
            Discount discount = findApplicable(code, subtotal);
            BigDecimal amount = discountCalculator.calculate(discount, subtotal);
            return new DiscountValidationResponse(discount.getCode(), true, amount, "Discount applied");
        } catch (BusinessRuleException ex) {
            return new DiscountValidationResponse(code, false, BigDecimal.ZERO, ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<Discount> findAll() {
        return discountRepository.findAll();
    }

    public Discount create(DiscountRequest request) {
        Discount discount = new Discount();
        apply(request, discount);
        discount.setUsedCount(0);
        return discountRepository.save(discount);
    }

    public Discount update(Long id, DiscountRequest request) {
        Discount discount = discountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Discount", id));
        apply(request, discount);
        return discount;
    }

    private void apply(DiscountRequest request, Discount discount) {
        discount.setCode(request.code().trim().toUpperCase(Locale.ROOT));
        discount.setDescription(request.description());
        discount.setType(request.type());
        discount.setValue(request.value());
        discount.setMinOrderAmount(request.minOrderAmount());
        discount.setValidFrom(request.validFrom());
        discount.setValidUntil(request.validUntil());
        discount.setMaxUses(request.maxUses());
        discount.setActive(request.active() == null || request.active());
    }
}
