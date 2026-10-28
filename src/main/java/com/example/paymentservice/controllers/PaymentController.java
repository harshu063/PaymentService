package com.example.paymentservice.controllers;

import com.example.paymentservice.dtos.CreatePaymentLinkRequestDto;
import com.example.paymentservice.dtos.CreatePaymentLinkResponseDto;
import com.example.paymentservice.services.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/payments")
public class PaymentController {
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping({"", "/"})
    public ResponseEntity<CreatePaymentLinkResponseDto> createPayment(
            @Valid @RequestBody CreatePaymentLinkRequestDto request,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String idempotencyKey,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(CreatePaymentLinkResponseDto.from(
                paymentService.create(request.orderId(), jwt.getSubject(), idempotencyKey, authorizationHeader)));
    }

    @GetMapping("/{paymentId}")
    public CreatePaymentLinkResponseDto getPayment(
            @PathVariable UUID paymentId,
            @AuthenticationPrincipal Jwt jwt) {
        return CreatePaymentLinkResponseDto.from(paymentService.get(paymentId, jwt.getSubject()));
    }
}
