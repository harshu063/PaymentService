package com.example.paymentservice.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provider_callback_inbox")
public class ProviderCallbackInbox {
    @Id
    private UUID id;

    @Column(name = "provider_event_id", nullable = false, unique = true, length = 200)
    private String providerEventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload_sha256", nullable = false, length = 64)
    private String payloadSha256;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected ProviderCallbackInbox() {
    }

    public ProviderCallbackInbox(UUID id, String providerEventId, String eventType,
                                 String payloadSha256, Instant receivedAt) {
        this.id = id;
        this.providerEventId = providerEventId;
        this.eventType = eventType;
        this.payloadSha256 = payloadSha256;
        this.receivedAt = receivedAt;
    }

    public void processed(Instant now) { this.processedAt = now; }
    public UUID getId() { return id; }
    public String getProviderEventId() { return providerEventId; }
    public Instant getProcessedAt() { return processedAt; }
}
