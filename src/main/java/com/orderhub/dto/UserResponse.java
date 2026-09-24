package com.orderhub.dto;

import com.orderhub.domain.Role;

import java.time.LocalDateTime;

public record UserResponse(Long id, String email, Role role, boolean enabled, LocalDateTime createdAt) {
}
