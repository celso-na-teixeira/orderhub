package com.orderhub.controller;

import com.orderhub.domain.OrderStatus;
import com.orderhub.dto.AuditEventResponse;
import com.orderhub.dto.CreateOrderRequest;
import com.orderhub.dto.OrderResponse;
import com.orderhub.dto.RefundRequest;
import com.orderhub.dto.ReplaceOrderItemsRequest;
import com.orderhub.dto.UpdateOrderStatusRequest;
import com.orderhub.mapper.OrderMapper;
import com.orderhub.security.AppUserDetails;
import com.orderhub.service.AuditService;
import com.orderhub.service.OrderService;
import com.orderhub.service.RefundService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders")
public class OrderController {

    private final OrderService orderService;
    private final RefundService refundService;
    private final AuditService auditService;
    private final OrderMapper orderMapper;

    public OrderController(OrderService orderService,
                           RefundService refundService,
                           AuditService auditService,
                           OrderMapper orderMapper) {
        this.orderService = orderService;
        this.refundService = refundService;
        this.auditService = auditService;
        this.orderMapper = orderMapper;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request,
                                                @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                @AuthenticationPrincipal AppUserDetails user) {
        return ResponseEntity.ok(orderMapper.toResponse(orderService.placeOrder(user.getId(), request, idempotencyKey)));
    }

    @GetMapping
    public Page<OrderResponse> list(@RequestParam(required = false) Long customerId,
                                    @RequestParam(required = false) OrderStatus status,
                                    @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @AuthenticationPrincipal AppUserDetails user) {
        Long effectiveCustomerId = customerId;
        if (effectiveCustomerId == null && user.isCustomer()) {
            effectiveCustomerId = orderService.customerIdForUser(user.getId());
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return orderService.listOrders(effectiveCustomerId, status, pageable).map(orderMapper::toResponse);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails user) {
        return orderMapper.toResponse(orderService.getOrder(id, user));
    }

    @PutMapping("/{id}/items")
    public OrderResponse replaceItems(@PathVariable Long id,
                                      @Valid @RequestBody ReplaceOrderItemsRequest request,
                                      @AuthenticationPrincipal AppUserDetails user) {
        return orderMapper.toResponse(orderService.replaceItems(id, request.items(), user));
    }

    @GetMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails user) {
        return orderMapper.toResponse(orderService.cancelOrder(id, user));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'STAFF')")
    public OrderResponse updateStatus(@PathVariable Long id,
                                      @Valid @RequestBody UpdateOrderStatusRequest request,
                                      @AuthenticationPrincipal AppUserDetails user) {
        return orderMapper.toResponse(orderService.updateStatus(id, request.status(), user));
    }

    @PostMapping("/{id}/refund")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'STAFF')")
    public OrderResponse refund(@PathVariable Long id, @Valid @RequestBody RefundRequest request) {
        return orderMapper.toResponse(refundService.refund(id, request.reason()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        orderService.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/history")
    public List<AuditEventResponse> history(@PathVariable Long id) {
        return auditService.historyFor("ORDER", id);
    }
}
