package com.orderhub.controller;

import com.orderhub.dto.PaymentRequest;
import com.orderhub.dto.PaymentResponse;
import com.orderhub.security.AppUserDetails;
import com.orderhub.service.PaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders/{orderId}/payments")
@Tag(name = "Payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse pay(@PathVariable Long orderId,
                               @Valid @RequestBody PaymentRequest request,
                               @AuthenticationPrincipal AppUserDetails user) {
        return paymentService.pay(orderId, request, user);
    }

    @GetMapping
    public List<PaymentResponse> list(@PathVariable Long orderId, @AuthenticationPrincipal AppUserDetails user) {
        return paymentService.findForOrder(orderId, user);
    }
}
