package com.orderhub.dto;

import com.orderhub.domain.CustomerTier;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UpdateCustomerRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        CustomerTier tier
) {
}
