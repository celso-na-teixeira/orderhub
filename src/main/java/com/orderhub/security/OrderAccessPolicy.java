package com.orderhub.security;

import com.orderhub.domain.Customer;
import com.orderhub.domain.Order;
import com.orderhub.repository.CustomerRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class OrderAccessPolicy {

    private final CustomerRepository customerRepository;

    public OrderAccessPolicy(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public void assertCanAccess(Order order, AppUserDetails currentUser) {
        if (currentUser.isStaffOrAdmin()) {
            return;
        }
        Customer customer = customerRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new AccessDeniedException("No customer profile for current user"));
        if (!customer.getId().equals(order.getCustomer().getId())) {
            throw new AccessDeniedException("Order does not belong to current customer");
        }
    }
}
