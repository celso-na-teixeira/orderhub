package com.orderhub.mapper;

import com.orderhub.domain.Customer;
import com.orderhub.dto.CustomerResponse;
import org.mapstruct.Mapper;

@Mapper
public interface CustomerMapper {

    CustomerResponse toResponse(Customer customer);
}
