package com.example.paymentservice.services;

import com.example.paymentservice.models.OutboxEvent;
import com.example.paymentservice.repositories.OutboxEventRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@ConditionalOnProperty(name = "payments.kafka.outbox.enabled", havingValue = "true")
public class PaymentOutboxPublisher {
    private final OutboxEventRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final String outcomeTopic;
    private final String lifecycleTopic;

    public PaymentOutboxPublisher(OutboxEventRepository outbox, KafkaTemplate<String, String> kafka,
                                  @org.springframework.beans.factory.annotation.Value("${payments.kafka.outbox.topic}") String outcomeTopic,
                                  @org.springframework.beans.factory.annotation.Value("${payments.kafka.outbox.lifecycle-topic}") String lifecycleTopic) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.outcomeTopic = outcomeTopic;
        this.lifecycleTopic = lifecycleTopic;
    }

    @Scheduled(fixedDelayString = "${payments.kafka.outbox.fixed-delay-ms}")
    @Transactional
    public void publishPending() {
        publish(outbox.findTop50ByPublishedAtIsNullOrderByOccurredAtAsc());
    }

    void publish(List<OutboxEvent> events) {
        for (OutboxEvent event : events) {
            try {
                kafka.send(topicFor(event), event.getOrderingKey(), event.getPayload()).get();
                event.markPublished(Instant.now());
            } catch (Exception failure) {
                throw new IllegalStateException("Could not publish payment outbox event " + event.getEventId(), failure);
            }
        }
    }

    private String topicFor(OutboxEvent event) {
        return event.getEventType().equals("payment.succeeded.v1") || event.getEventType().equals("payment.failed.v1")
                ? outcomeTopic : lifecycleTopic;
    }
}
