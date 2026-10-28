package com.example.paymentservice.repositories;

import com.example.paymentservice.models.ProviderCallbackInbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProviderCallbackInboxRepository extends JpaRepository<ProviderCallbackInbox, UUID> {
    boolean existsByProviderEventId(String providerEventId);
}
