package com.example.paymentservice.repositories;

import com.example.paymentservice.models.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {
    Optional<PaymentAttempt> findBySubjectIdAndIdempotencyKey(String subjectId, String idempotencyKey);
    Optional<PaymentAttempt> findByIdAndSubjectId(UUID id, String subjectId);
    Optional<PaymentAttempt> findByProviderAndProviderReference(String provider, String providerReference);
}
