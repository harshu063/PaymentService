package com.example.paymentservice.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_attempt", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_subject_idempotency", columnNames = {"subject_id", "idempotency_key"}),
        @UniqueConstraint(name = "uk_payment_provider_reference", columnNames = {"provider", "provider_reference"})
})
public class PaymentAttempt {
    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "subject_id", nullable = false, length = 200)
    private String subjectId;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "provider_reference", length = 200)
    private String providerReference;

    @Column(name = "redirect_url", length = 2048)
    private String redirectUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected PaymentAttempt() {
    }

    public PaymentAttempt(UUID id, UUID orderId, String subjectId, String idempotencyKey,
                          long amountMinor, String currency, String provider, Instant now) {
        this.id = id;
        this.orderId = orderId;
        this.subjectId = subjectId;
        this.idempotencyKey = idempotencyKey;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.provider = provider;
        this.status = PaymentStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void requiresAction(String providerReference, String redirectUrl, Instant now) {
        this.providerReference = providerReference;
        this.redirectUrl = redirectUrl;
        this.status = PaymentStatus.REQUIRES_ACTION;
        this.updatedAt = now;
    }

    public void unknown(Instant now) {
        if (status != PaymentStatus.SUCCEEDED) {
            status = PaymentStatus.UNKNOWN;
            updatedAt = now;
        }
    }

    public boolean succeed(Instant now) {
        if (status == PaymentStatus.SUCCEEDED || status == PaymentStatus.FAILED) {
            return false;
        }
        status = PaymentStatus.SUCCEEDED;
        updatedAt = now;
        return true;
    }

    public boolean fail(Instant now) {
        if (status == PaymentStatus.SUCCEEDED || status == PaymentStatus.FAILED) {
            return false;
        }
        status = PaymentStatus.FAILED;
        updatedAt = now;
        return true;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public String getSubjectId() { return subjectId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public String getProvider() { return provider; }
    public String getProviderReference() { return providerReference; }
    public String getRedirectUrl() { return redirectUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
