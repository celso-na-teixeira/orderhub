package com.orderhub.mapper;

import com.orderhub.domain.Customer;
import com.orderhub.dto.CustomerResponse;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

    public CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getUserId(),
                customer.getName(),
                customer.getEmail(),
                customer.getTier(),
                customer.getCreatedAt());
    }
}
