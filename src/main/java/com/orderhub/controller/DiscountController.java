package com.orderhub.controller;

import com.orderhub.dto.DiscountRequest;
import com.orderhub.dto.DiscountResponse;
import com.orderhub.dto.DiscountValidationResponse;
import com.orderhub.mapper.DiscountMapper;
import com.orderhub.service.DiscountService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/discounts")
@Tag(name = "Discounts")
public class DiscountController {

    private final DiscountService discountService;
    private final DiscountMapper discountMapper;

    public DiscountController(DiscountService discountService, DiscountMapper discountMapper) {
        this.discountService = discountService;
        this.discountMapper = discountMapper;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public List<DiscountResponse> list() {
        return discountService.findAll().stream().map(discountMapper::toResponse).toList();
    }

    @GetMapping("/validate")
    public DiscountValidationResponse validate(@RequestParam String code, @RequestParam BigDecimal subtotal) {
        return discountService.validate(code, subtotal);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ADMIN')")
    public DiscountResponse create(@Valid @RequestBody DiscountRequest request) {
        return discountMapper.toResponse(discountService.create(request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public DiscountResponse update(@PathVariable Long id, @Valid @RequestBody DiscountRequest request) {
        return discountMapper.toResponse(discountService.update(id, request));
    }
}
