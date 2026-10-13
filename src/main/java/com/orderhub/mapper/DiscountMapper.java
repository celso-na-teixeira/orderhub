package com.orderhub.mapper;

import com.orderhub.domain.Discount;
import com.orderhub.dto.DiscountResponse;
import org.mapstruct.Mapper;

@Mapper
public interface DiscountMapper {

    DiscountResponse toResponse(Discount discount);
}
