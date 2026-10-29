package com.example.paymentservice.services;

import com.example.paymentservice.models.OutboxEvent;
import com.example.paymentservice.repositories.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentOutboxPublisherTest {
    @Test
    void publishesThePersistedCanonicalPayloadUsingItsOrderingKey() throws Exception {
        OutboxEventRepository outbox = mock(OutboxEventRepository.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        OutboxEvent event = new OutboxEvent(UUID.randomUUID(), UUID.randomUUID(), "payment.succeeded.v1",
                "tenant-a:order-42", "{\"eventType\":\"payment.succeeded.v1\"}", Instant.now());
        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> sent = mock(CompletableFuture.class);
        when(kafka.send("payments.outcomes.v1", "tenant-a:order-42", event.getPayload())).thenReturn(sent);
        when(sent.get()).thenReturn(mock(SendResult.class));

        new PaymentOutboxPublisher(outbox, kafka, "payments.outcomes.v1", "payments.lifecycle.v1").publish(List.of(event));

        verify(kafka).send("payments.outcomes.v1", "tenant-a:order-42", event.getPayload());
    }
}
