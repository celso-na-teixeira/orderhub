package com.orderhub.dto;

import java.time.LocalDateTime;

public record AuditEventResponse(String action, String actor, String details, LocalDateTime createdAt) {
}
