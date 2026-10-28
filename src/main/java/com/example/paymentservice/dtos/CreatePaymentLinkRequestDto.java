package com.example.paymentservice.dtos;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreatePaymentLinkRequestDto(@NotNull UUID orderId) {
}
