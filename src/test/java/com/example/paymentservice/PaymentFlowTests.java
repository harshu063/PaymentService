package com.example.paymentservice;

import com.example.paymentservice.adapters.paymentGatewayAdapters.PaymentGatewayAdapter;
import com.example.paymentservice.models.PaymentAttempt;
import com.example.paymentservice.models.PaymentStatus;
import com.example.paymentservice.repositories.OutboxEventRepository;
import com.example.paymentservice.repositories.PaymentAttemptRepository;
import com.example.paymentservice.repositories.ProviderCallbackInboxRepository;
import com.example.paymentservice.services.OrderSnapshotClient;
import com.example.paymentservice.services.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@SpringBootTest
class PaymentFlowTests {
    @Autowired
    private PaymentService payments;
    @Autowired
    private PaymentAttemptRepository attempts;
    @Autowired
    private ProviderCallbackInboxRepository callbacks;
    @Autowired
    private OutboxEventRepository outbox;

    @BeforeEach
    void clearPaymentState() {
        callbacks.deleteAll();
        outbox.deleteAll();
        attempts.deleteAll();
    }

    @MockitoBean
    private PaymentGatewayAdapter gateway;
    @MockitoBean
    private OrderSnapshotClient orders;

    @Test
    void repeatedCreateAndProviderCallbackAreIdempotent() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orders.get(orderId, "Bearer test")).thenReturn(
                new OrderSnapshotClient.OrderSnapshot(orderId, "PENDING_PAYMENT", 2599, "INR", 1));
        when(gateway.createCheckout(any(), eq(orderId), eq(2599L), eq("INR"), anyString()))
                .thenReturn(new PaymentGatewayAdapter.ProviderCheckout("cs_test_1", "https://checkout.test/1"));

        PaymentAttempt first = payments.create(orderId, "user-1", "checkout-1", "Bearer test");
        PaymentAttempt repeated = payments.create(orderId, "user-1", "checkout-1", "Bearer test");

        assertThat(repeated.getId()).isEqualTo(first.getId());
        assertThat(first.getStatus()).isEqualTo(PaymentStatus.REQUIRES_ACTION);
        assertThat(attempts.count()).isEqualTo(1);
        assertThat(outbox.count()).isEqualTo(1);
        verify(gateway, times(1)).createCheckout(any(), eq(orderId), anyLong(), anyString(), anyString());
        verify(orders, times(1)).get(orderId, "Bearer test");

        PaymentGatewayAdapter.VerifiedWebhook webhook = new PaymentGatewayAdapter.VerifiedWebhook(
                "evt_1", "checkout.session.completed", "cs_test_1", first.getId(),
                PaymentGatewayAdapter.Outcome.SUCCEEDED);
        when(gateway.verifyWebhook("{}", "signature")).thenReturn(webhook);

        payments.handleWebhook("{}", "signature");
        payments.handleWebhook("{}", "signature");

        assertThat(attempts.findById(first.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(callbacks.count()).isEqualTo(1);
        assertThat(outbox.count()).isEqualTo(2);
    }

    @Test
    void rejectsOrderThatIsNotAwaitingPaymentBeforeCreatingAnAttempt() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orders.get(orderId, "Bearer test")).thenReturn(
                new OrderSnapshotClient.OrderSnapshot(orderId, "CANCELLED", 2599, "INR", 2));

        assertThatThrownBy(() -> payments.create(orderId, "user-cancelled", "checkout-cancelled", "Bearer test"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");

        assertThat(attempts.findBySubjectIdAndIdempotencyKey("user-cancelled", "checkout-cancelled"))
                .isEmpty();
        verify(gateway, times(0)).createCheckout(any(), any(), anyLong(), anyString(), anyString());
    }

    @Test
    void rejectsInvalidAuthoritativeOrderAmountsBeforeCreatingAnAttempt() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orders.get(orderId, "Bearer test")).thenReturn(
                new OrderSnapshotClient.OrderSnapshot(orderId, "PENDING_PAYMENT", 0, "INR", 1));

        assertThatThrownBy(() -> payments.create(orderId, "user-invalid", "checkout-invalid", "Bearer test"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("502 BAD_GATEWAY");

        assertThat(attempts.findBySubjectIdAndIdempotencyKey("user-invalid", "checkout-invalid"))
                .isEmpty();
        verify(gateway, times(0)).createCheckout(any(), any(), anyLong(), anyString(), anyString());
    }

    @Test
    void rejectsIdempotencyKeyReusedForAnotherOrder() throws Exception {
        UUID firstOrderId = UUID.randomUUID();
        UUID secondOrderId = UUID.randomUUID();
        when(orders.get(firstOrderId, "Bearer test")).thenReturn(
                new OrderSnapshotClient.OrderSnapshot(firstOrderId, "PENDING_PAYMENT", 2599, "INR", 1));
        when(gateway.createCheckout(any(), eq(firstOrderId), eq(2599L), eq("INR"), anyString()))
                .thenReturn(new PaymentGatewayAdapter.ProviderCheckout("cs_test_reuse", "https://checkout.test/reuse"));

        PaymentAttempt first = payments.create(firstOrderId, "user-reuse", "same-idempotency-key", "Bearer test");

        assertThatThrownBy(() -> payments.create(secondOrderId, "user-reuse", "same-idempotency-key", "Bearer test"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
        assertThat(attempts.findById(first.getId())).isPresent();
        verify(orders, times(0)).get(secondOrderId, "Bearer test");
        verify(gateway, times(1)).createCheckout(any(), eq(firstOrderId), anyLong(), anyString(), anyString());
    }

    @Test
    void ignoresLateSuccessAfterProviderAlreadyReportedFailure() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orders.get(orderId, "Bearer test")).thenReturn(
                new OrderSnapshotClient.OrderSnapshot(orderId, "PENDING_PAYMENT", 2599, "INR", 1));
        when(gateway.createCheckout(any(), eq(orderId), eq(2599L), eq("INR"), anyString()))
                .thenReturn(new PaymentGatewayAdapter.ProviderCheckout("cs_test_late", "https://checkout.test/late"));
        PaymentAttempt attempt = payments.create(orderId, "user-late", "checkout-late", "Bearer test");

        when(gateway.verifyWebhook("failed", "signature")).thenReturn(new PaymentGatewayAdapter.VerifiedWebhook(
                "evt_failed", "checkout.session.expired", "cs_test_late", attempt.getId(),
                PaymentGatewayAdapter.Outcome.FAILED));
        when(gateway.verifyWebhook("succeeded", "signature")).thenReturn(new PaymentGatewayAdapter.VerifiedWebhook(
                "evt_succeeded", "checkout.session.completed", "cs_test_late", attempt.getId(),
                PaymentGatewayAdapter.Outcome.SUCCEEDED));

        payments.handleWebhook("failed", "signature");
        payments.handleWebhook("succeeded", "signature");

        assertThat(attempts.findById(attempt.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(outbox.count()).isEqualTo(2);
    }

    @Test
    void rejectsUnverifiedCallbackWithoutPersistingAnInboxRecord() throws Exception {
        when(gateway.verifyWebhook("forged", "bad-signature"))
                .thenThrow(new com.stripe.exception.SignatureVerificationException("invalid", "bad-signature"));

        assertThatThrownBy(() -> payments.handleWebhook("forged", "bad-signature"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");

        assertThat(callbacks.count()).isZero();
        assertThat(outbox.count()).isZero();
    }
}
