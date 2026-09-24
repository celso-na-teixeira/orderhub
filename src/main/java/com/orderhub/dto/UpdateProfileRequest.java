package com.orderhub.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Email String email,
        String currentPassword,
        @Size(min = 8, max = 72) String newPassword
) {
}
