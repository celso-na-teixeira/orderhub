package com.orderhub.controller;

import com.orderhub.dto.CustomerResponse;
import com.orderhub.dto.UpdateCustomerRequest;
import com.orderhub.mapper.CustomerMapper;
import com.orderhub.security.AppUserDetails;
import com.orderhub.service.CustomerService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
@Tag(name = "Customers")
public class CustomerController {

    private final CustomerService customerService;
    private final CustomerMapper customerMapper;

    public CustomerController(CustomerService customerService, CustomerMapper customerMapper) {
        this.customerService = customerService;
        this.customerMapper = customerMapper;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'STAFF')")
    public Page<CustomerResponse> list(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return customerService.findAll(PageRequest.of(page, size)).map(customerMapper::toResponse);
    }

    @GetMapping("/me")
    public CustomerResponse me(@AuthenticationPrincipal AppUserDetails user) {
        return customerMapper.toResponse(customerService.getByUserId(user.getId()));
    }

    @GetMapping("/{id}")
    public CustomerResponse get(@PathVariable Long id) {
        return customerMapper.toResponse(customerService.getById(id));
    }

    @PutMapping("/{id}")
    public CustomerResponse update(@PathVariable Long id,
                                   @Valid @RequestBody UpdateCustomerRequest request,
                                   @AuthenticationPrincipal AppUserDetails user) {
        return customerMapper.toResponse(customerService.update(id, request, user));
    }
}
