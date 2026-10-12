package com.orderhub.dto;

import com.orderhub.domain.CustomerTier;

import java.time.LocalDateTime;

public record CustomerResponse(Long id, Long userId, String name, String email, CustomerTier tier, LocalDateTime createdAt) {
}
