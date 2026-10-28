package com.example.paymentservice.services;

import com.example.paymentservice.adapters.paymentGatewayAdapters.PaymentGatewayAdapter;
import com.example.paymentservice.models.OutboxEvent;
import com.example.paymentservice.models.PaymentAttempt;
import com.example.paymentservice.models.ProviderCallbackInbox;
import com.example.paymentservice.repositories.OutboxEventRepository;
import com.example.paymentservice.repositories.PaymentAttemptRepository;
import com.example.paymentservice.repositories.ProviderCallbackInboxRepository;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentAttemptTransactions {
    private final PaymentAttemptRepository attempts;
    private final ProviderCallbackInboxRepository callbacks;
    private final OutboxEventRepository outbox;
    private final JsonMapper objectMapper;
    private final Clock clock;

    @Autowired
    public PaymentAttemptTransactions(PaymentAttemptRepository attempts,
                                      ProviderCallbackInboxRepository callbacks,
                                      OutboxEventRepository outbox,
                                      JsonMapper objectMapper) {
        this(attempts, callbacks, outbox, objectMapper, Clock.systemUTC());
    }

    PaymentAttemptTransactions(PaymentAttemptRepository attempts,
                               ProviderCallbackInboxRepository callbacks,
                               OutboxEventRepository outbox,
                               JsonMapper objectMapper,
                               Clock clock) {
        this.attempts = attempts;
        this.callbacks = callbacks;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<PaymentAttempt> findIdempotent(String subjectId, String idempotencyKey) {
        return attempts.findBySubjectIdAndIdempotencyKey(subjectId, idempotencyKey);
    }

    @Transactional(readOnly = true)
    public Optional<PaymentAttempt> findOwned(UUID id, String subjectId) {
        return attempts.findByIdAndSubjectId(id, subjectId);
    }

    @Transactional
    public PaymentAttempt createPending(UUID orderId, String subjectId, String idempotencyKey,
                                        long amountMinor, String currency) {
        Instant now = clock.instant();
        return attempts.saveAndFlush(new PaymentAttempt(
                UUID.randomUUID(), orderId, subjectId, idempotencyKey,
                amountMinor, currency, "STRIPE", now));
    }

    @Transactional
    public PaymentAttempt requiresAction(UUID id, PaymentGatewayAdapter.ProviderCheckout checkout) {
        PaymentAttempt attempt = required(id);
        attempt.requiresAction(checkout.providerReference(), checkout.redirectUrl(), clock.instant());
        attempts.flush();
        addOutbox(attempt, "payment.link-created.v1");
        return attempt;
    }

    @Transactional
    public void markUnknown(UUID id) {
        required(id).unknown(clock.instant());
    }

    @Transactional
    public boolean processWebhook(PaymentGatewayAdapter.VerifiedWebhook event, String payloadHash) {
        if (callbacks.existsByProviderEventId(event.eventId())) {
            return false;
        }
        Instant now = clock.instant();
        ProviderCallbackInbox callback = callbacks.saveAndFlush(new ProviderCallbackInbox(
                UUID.randomUUID(), event.eventId(), event.eventType(), payloadHash, now));
        if (event.outcome() == PaymentGatewayAdapter.Outcome.IGNORED) {
            callback.processed(now);
            return true;
        }

        PaymentAttempt attempt = event.paymentId() == null
                ? attempts.findByProviderAndProviderReference("STRIPE", event.providerReference())
                    .orElseThrow(() -> new IllegalStateException("Payment attempt not found for verified callback"))
                : attempts.findById(event.paymentId())
                    .orElseThrow(() -> new IllegalStateException("Payment attempt not found for verified callback"));

        boolean changed = switch (event.outcome()) {
            case SUCCEEDED -> attempt.succeed(now);
            case FAILED -> attempt.fail(now);
            case IGNORED -> false;
        };
        if (changed) {
            attempts.flush();
            addOutbox(attempt, event.outcome() == PaymentGatewayAdapter.Outcome.SUCCEEDED
                    ? "payment.succeeded.v1" : "payment.failed.v1");
        }
        callback.processed(now);
        return true;
    }

    private PaymentAttempt required(UUID id) {
        return attempts.findById(id).orElseThrow(() -> new IllegalStateException("Payment attempt disappeared"));
    }

    private void addOutbox(PaymentAttempt attempt, String eventType) {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = clock.instant();
        String payload = objectMapper.writeValueAsString(Map.of(
                    "eventId", eventId,
                    "eventType", eventType,
                    "occurredAt", occurredAt,
                    "orderingKey", "order:" + attempt.getOrderId(),
                    "payload", Map.of(
                            "paymentId", attempt.getId(),
                            "orderId", attempt.getOrderId(),
                            "amountMinor", attempt.getAmountMinor(),
                            "currency", attempt.getCurrency(),
                            "paymentVersion", attempt.getVersion())));
        outbox.save(new OutboxEvent(eventId, attempt.getId(), eventType,
                "order:" + attempt.getOrderId(), payload, occurredAt));
    }
}
