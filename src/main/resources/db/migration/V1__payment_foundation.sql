CREATE TABLE payment_attempt (
    id BINARY(16) NOT NULL,
    order_id BINARY(16) NOT NULL,
    subject_id VARCHAR(200) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    provider_reference VARCHAR(200) NULL,
    redirect_url VARCHAR(2048) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_payment_attempt PRIMARY KEY (id),
    CONSTRAINT uk_payment_subject_idempotency UNIQUE (subject_id, idempotency_key),
    CONSTRAINT uk_payment_provider_reference UNIQUE (provider, provider_reference),
    CONSTRAINT ck_payment_amount_nonnegative CHECK (amount_minor >= 0)
);

CREATE INDEX idx_payment_order ON payment_attempt (order_id);
CREATE INDEX idx_payment_status_updated ON payment_attempt (status, updated_at);

CREATE TABLE provider_callback_inbox (
    id BINARY(16) NOT NULL,
    provider_event_id VARCHAR(200) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload_sha256 VARCHAR(64) NOT NULL,
    received_at DATETIME(6) NOT NULL,
    processed_at DATETIME(6) NULL,
    CONSTRAINT pk_provider_callback_inbox PRIMARY KEY (id),
    CONSTRAINT uk_callback_provider_event UNIQUE (provider_event_id)
);

CREATE TABLE outbox_event (
    event_id BINARY(16) NOT NULL,
    aggregate_id BINARY(16) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    ordering_key VARCHAR(250) NOT NULL,
    payload TEXT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    published_at DATETIME(6) NULL,
    CONSTRAINT pk_outbox_event PRIMARY KEY (event_id)
);

CREATE INDEX idx_outbox_unpublished ON outbox_event (published_at, occurred_at);
