package com.orderhub.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ReplaceOrderItemsRequest(@NotEmpty List<@Valid OrderLineRequest> items) {
}
