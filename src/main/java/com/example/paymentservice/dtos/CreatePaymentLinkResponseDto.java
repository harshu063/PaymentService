package com.example.paymentservice.dtos;

import com.example.paymentservice.models.PaymentAttempt;
import com.example.paymentservice.models.PaymentStatus;

import java.util.UUID;

public record CreatePaymentLinkResponseDto(
        UUID paymentId,
        UUID orderId,
        PaymentStatus status,
        long amountMinor,
        String currency,
        String redirectUrl) {

    public static CreatePaymentLinkResponseDto from(PaymentAttempt attempt) {
        return new CreatePaymentLinkResponseDto(
                attempt.getId(), attempt.getOrderId(), attempt.getStatus(),
                attempt.getAmountMinor(), attempt.getCurrency(), attempt.getRedirectUrl());
    }
}
