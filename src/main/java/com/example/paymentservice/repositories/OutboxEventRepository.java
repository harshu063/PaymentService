package com.example.paymentservice.repositories;

import com.example.paymentservice.models.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findTop50ByPublishedAtIsNullOrderByOccurredAtAsc();
}
