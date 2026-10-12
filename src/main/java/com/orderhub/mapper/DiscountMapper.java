package com.orderhub.mapper;

import com.orderhub.domain.Discount;
import com.orderhub.dto.DiscountResponse;
import org.springframework.stereotype.Component;

@Component
public class DiscountMapper {

    public DiscountResponse toResponse(Discount discount) {
        return new DiscountResponse(
                discount.getId(),
                discount.getCode(),
                discount.getDescription(),
                discount.getType(),
                discount.getValue(),
                discount.getMinOrderAmount(),
                discount.getValidFrom(),
                discount.getValidUntil(),
                discount.getMaxUses(),
                discount.getUsedCount(),
                discount.isActive());
    }
}
