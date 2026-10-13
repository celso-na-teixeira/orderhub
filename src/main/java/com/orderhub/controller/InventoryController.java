package com.orderhub.controller;

import com.orderhub.dto.InventoryAdjustmentRequest;
import com.orderhub.dto.InventoryResponse;
import com.orderhub.dto.LowStockResponse;
import com.orderhub.mapper.InventoryMapper;
import com.orderhub.service.InventoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
@Tag(name = "Inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryMapper inventoryMapper;

    @GetMapping("/{productId}")
    public InventoryResponse get(@PathVariable Long productId) {
        return inventoryMapper.toResponse(inventoryService.getByProductId(productId));
    }

    @PutMapping("/{productId}")
    public ResponseEntity<?> adjust(@PathVariable Long productId,
                                    @Valid @RequestBody InventoryAdjustmentRequest request) {
        if (request.reason() == null || request.reason().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "A reason is required for stock adjustments"));
        }
        return ResponseEntity.ok(inventoryMapper.toResponse(
                inventoryService.adjust(productId, request.quantityOnHand(), request.reason())));
    }

    @GetMapping("/low-stock")
    public List<LowStockResponse> lowStock(@RequestParam(defaultValue = "5") int threshold) {
        return inventoryService.findLowStock(threshold).stream()
                .map(inventoryMapper::toLowStock)
                .toList();
    }
}
