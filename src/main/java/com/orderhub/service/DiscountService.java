package com.orderhub.service;

import com.orderhub.domain.Discount;
import com.orderhub.domain.DiscountType;
import com.orderhub.dto.DiscountRequest;
import com.orderhub.dto.DiscountValidationResponse;
import com.orderhub.exception.BusinessRuleException;
import com.orderhub.exception.ConflictException;
import com.orderhub.exception.DiscountNotApplicableException;
import com.orderhub.exception.DiscountNotApplicableException.Reason;
import com.orderhub.exception.ResourceNotFoundException;
import com.orderhub.repository.DiscountRepository;
import com.orderhub.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class DiscountService {

    private final DiscountRepository discountRepository;
    private final DiscountCalculator discountCalculator;

    /**
     * Resolves a discount code and verifies that it can be used for an order with the given subtotal.
     */
    @Transactional(readOnly = true)
    public Discount findApplicable(String code, BigDecimal subtotal) {
        return resolve(code, subtotal, true);
    }

    /**
     * Resolves a discount code that is already on an order and verifies that it still fits the given subtotal.
     * Its use has already been counted, so the usage limit is not checked again.
     */
    @Transactional(readOnly = true)
    public Discount findAlreadyApplied(String code, BigDecimal subtotal) {
        return resolve(code, subtotal, false);
    }

    private Discount resolve(String code, BigDecimal subtotal, boolean checkUsageLimit) {
        Discount discount = discountRepository.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new DiscountNotApplicableException(code, Reason.UNKNOWN_CODE,
                        "Unknown discount code: " + code));

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (!discount.isActive()) {
            throw new DiscountNotApplicableException(code, Reason.INACTIVE,
                    "Discount " + code + " is no longer active");
        }
        if (discount.getValidFrom() != null && now.isBefore(discount.getValidFrom())) {
            throw new DiscountNotApplicableException(code, Reason.NOT_YET_VALID,
                    "Discount " + code + " is not valid yet");
        }
        if (discount.getValidUntil() != null && now.isAfter(discount.getValidUntil())) {
            throw new DiscountNotApplicableException(code, Reason.EXPIRED,
                    "Discount " + code + " has expired");
        }
        if (checkUsageLimit && discount.getMaxUses() != null && discount.getUsedCount() >= discount.getMaxUses()) {
            throw new DiscountNotApplicableException(code, Reason.USAGE_LIMIT_REACHED,
                    "Discount " + code + " has reached its usage limit");
        }
        if (discount.getMinOrderAmount() != null && subtotal.compareTo(discount.getMinOrderAmount()) < 0) {
            throw new DiscountNotApplicableException(code, Reason.BELOW_MINIMUM_ORDER_AMOUNT,
                    "Order subtotal is below the minimum of " + discount.getMinOrderAmount() + " required for " + code);
        }
        return discount;
    }

    public void markUsed(Discount discount) {
        if (discountRepository.incrementUsage(discount.getId()) == 0) {
            log.warn("Discount {} ran out of uses before it could be counted for this order", discount.getCode());
            throw new DiscountNotApplicableException(discount.getCode(), Reason.USAGE_LIMIT_REACHED,
                    "Discount " + discount.getCode() + " has reached its usage limit");
        }
        log.debug("Discount {} marked as used", discount.getCode());
    }

    @Transactional(readOnly = true)
    public DiscountValidationResponse validate(String code, BigDecimal subtotal) {
        try {
            Discount discount = findApplicable(code, subtotal);
            BigDecimal amount = discountCalculator.calculate(discount, subtotal);
            return new DiscountValidationResponse(discount.getCode(), true, amount, "Discount applied");
        } catch (DiscountNotApplicableException ex) {
            log.info("Discount {} rejected for subtotal {}: {}", code, subtotal, ex.getReason());
            return new DiscountValidationResponse(code, false, BigDecimal.ZERO, ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<Discount> findAll() {
        return discountRepository.findAll();
    }

    public Discount create(DiscountRequest request) {
        if (discountRepository.existsByCodeIgnoreCase(normalize(request.code()))) {
            throw new ConflictException("Discount code " + normalize(request.code()) + " already exists");
        }
        Discount discount = new Discount();
        apply(request, discount);
        discount.setUsedCount(0);
        Discount saved = discountRepository.save(discount);
        log.info("Created discount {} ({})", saved.getCode(), saved.getId());
        return saved;
    }

    public Discount update(Long id, DiscountRequest request) {
        Discount discount = discountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Discount", id));
        if (discountRepository.existsByCodeIgnoreCaseAndIdNot(normalize(request.code()), id)) {
            throw new ConflictException("Discount code " + normalize(request.code()) + " already exists");
        }
        apply(request, discount);
        log.info("Updated discount {} ({})", discount.getCode(), id);
        return discount;
    }

    private void apply(DiscountRequest request, Discount discount) {
        if (request.type() == DiscountType.PERCENTAGE && request.value().compareTo(MoneyUtils.ONE_HUNDRED) > 0) {
            throw new BusinessRuleException("A percentage discount cannot be more than 100");
        }
        discount.setCode(normalize(request.code()));
        discount.setDescription(request.description());
        discount.setType(request.type());
        discount.setValue(request.value());
        discount.setMinOrderAmount(request.minOrderAmount());
        discount.setValidFrom(request.validFrom());
        discount.setValidUntil(request.validUntil());
        discount.setMaxUses(request.maxUses());
        discount.setActive(request.active() == null || request.active());
    }

    private static String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
