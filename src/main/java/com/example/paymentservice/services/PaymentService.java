package com.example.paymentservice.services;

import com.example.paymentservice.adapters.paymentGatewayAdapters.PaymentGatewayAdapter;
import com.example.paymentservice.models.PaymentAttempt;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class PaymentService {
    private final PaymentAttemptTransactions transactions;
    private final OrderSnapshotClient orders;
    private final PaymentGatewayAdapter gateway;

    public PaymentService(PaymentAttemptTransactions transactions,
                          OrderSnapshotClient orders,
                          PaymentGatewayAdapter gateway) {
        this.transactions = transactions;
        this.orders = orders;
        this.gateway = gateway;
    }

    public PaymentAttempt create(UUID orderId, String subjectId, String idempotencyKey,
                                 String authorizationHeader) {
        PaymentAttempt existing = transactions.findIdempotent(subjectId, idempotencyKey).orElse(null);
        if (existing != null) {
            ensureSameOrder(existing, orderId);
            return existing;
        }

        OrderSnapshotClient.OrderSnapshot order = orders.get(orderId, authorizationHeader);
        if (order == null || !orderId.equals(order.orderId())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Order service returned an invalid order");
        }
        if (!"PENDING_PAYMENT".equals(order.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order is not awaiting payment");
        }
        if (order.totalMinor() <= 0 || order.currency() == null || !order.currency().matches("[A-Z]{3}")) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Order has invalid payment values");
        }

        PaymentAttempt attempt;
        try {
            attempt = transactions.createPending(orderId, subjectId, idempotencyKey,
                    order.totalMinor(), order.currency());
        } catch (DataIntegrityViolationException race) {
            PaymentAttempt concurrent = transactions.findIdempotent(subjectId, idempotencyKey).orElseThrow(() -> race);
            ensureSameOrder(concurrent, orderId);
            return concurrent;
        }

        try {
            PaymentGatewayAdapter.ProviderCheckout checkout = gateway.createCheckout(
                    attempt.getId(), orderId, attempt.getAmountMinor(), attempt.getCurrency(),
                    "payment-attempt-" + attempt.getId());
            return transactions.requiresAction(attempt.getId(), checkout);
        } catch (Exception providerFailure) {
            transactions.markUnknown(attempt.getId());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Payment provider outcome is unknown; retry with the same idempotency key", providerFailure);
        }
    }

    public PaymentAttempt get(UUID paymentId, String subjectId) {
        return transactions.findOwned(paymentId, subjectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public void handleWebhook(String payload, String signature) {
        try {
            PaymentGatewayAdapter.VerifiedWebhook event = gateway.verifyWebhook(payload, signature);
            transactions.processWebhook(event, sha256(payload));
        } catch (com.stripe.exception.SignatureVerificationException invalidSignature) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook signature");
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Verified webhook could not be processed", exception);
        }
    }

    private static void ensureSameOrder(PaymentAttempt attempt, UUID requestedOrderId) {
        if (!attempt.getOrderId().equals(requestedOrderId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Idempotency key was already used for a different order");
        }
    }

    private static String sha256(String payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
