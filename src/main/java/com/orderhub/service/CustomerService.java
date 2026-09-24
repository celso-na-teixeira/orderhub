package com.orderhub.service;

import com.orderhub.domain.Customer;
import com.orderhub.dto.UpdateCustomerRequest;
import com.orderhub.exception.ResourceNotFoundException;
import com.orderhub.repository.CustomerRepository;
import com.orderhub.security.AppUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public Page<Customer> findAll(Pageable pageable) {
        return customerRepository.findAll(pageable);
    }

    public Customer getById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }

    public Customer getByUserId(Long userId) {
        return customerRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer for user", userId));
    }

    public Customer update(Long id, UpdateCustomerRequest request, AppUserDetails currentUser) {
        Customer customer = getById(id);
        if (currentUser.isCustomer() && !customer.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Customers can only update their own profile");
        }
        customer.setName(request.name());
        customer.setEmail(request.email());
        if (request.tier() != null) {
            customer.setTier(request.tier());
        }
        log.info("Customer {} updated by {}", id, currentUser.getUsername());
        return customer;
    }
}
