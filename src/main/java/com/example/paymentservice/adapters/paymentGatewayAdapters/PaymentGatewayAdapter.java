package com.example.paymentservice.adapters.paymentGatewayAdapters;

import java.util.UUID;

public interface PaymentGatewayAdapter {
    ProviderCheckout createCheckout(UUID paymentId, UUID orderId, long amountMinor,
                                    String currency, String idempotencyKey) throws Exception;

    VerifiedWebhook verifyWebhook(String payload, String signature) throws Exception;

    record ProviderCheckout(String providerReference, String redirectUrl) {
    }

    record VerifiedWebhook(String eventId, String eventType, String providerReference,
                           UUID paymentId, Outcome outcome) {
    }

    enum Outcome { SUCCEEDED, FAILED, IGNORED }
}
