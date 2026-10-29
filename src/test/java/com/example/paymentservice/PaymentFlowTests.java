package com.example.paymentservice;

import com.example.paymentservice.adapters.paymentGatewayAdapters.PaymentGatewayAdapter;
import com.example.paymentservice.models.PaymentAttempt;
import com.example.paymentservice.models.PaymentStatus;
import com.example.paymentservice.repositories.OutboxEventRepository;
import com.example.paymentservice.repositories.PaymentAttemptRepository;
import com.example.paymentservice.repositories.ProviderCallbackInboxRepository;
import com.example.paymentservice.services.OrderSnapshotClient;
import com.example.paymentservice.services.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
    @Autowired
    private JsonMapper objectMapper;

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

        JsonNode outcome = objectMapper.readTree(outbox.findAll().stream()
                .filter(event -> event.getEventType().equals("payment.succeeded.v1"))
                .findFirst()
                .orElseThrow()
                .getPayload());

        assertThat(outcome.path("eventId").asText()).isNotBlank();
        assertThat(outcome.path("eventType").asText()).isEqualTo("payment.succeeded.v1");
        assertThat(outcome.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(outcome.path("tenantId").asText()).isEqualTo("tenant-default");
        assertThat(outcome.path("source").asText()).isEqualTo("payment-service");
        assertThat(outcome.path("subject").path("type").asText()).isEqualTo("payment");
        assertThat(outcome.path("subject").path("id").asText()).isEqualTo(first.getId().toString());
        assertThat(outcome.path("orderingKey").asText()).isEqualTo("tenant-default:" + orderId);
        assertThat(outcome.path("occurredAt").asText()).isNotBlank();
        assertThat(outcome.path("correlationId").asText()).isEqualTo("checkout-1");
        assertThat(outcome.path("causationId").asText()).isEqualTo("evt_1");
        assertThat(outcome.path("payload").path("paymentId").asText()).isEqualTo(first.getId().toString());
        assertThat(outcome.path("payload").path("orderId").asText()).isEqualTo(orderId.toString());
        assertThat(outcome.path("payload").path("amountMinor").asLong()).isEqualTo(2599L);
        assertThat(outcome.path("payload").path("currency").asText()).isEqualTo("INR");
    }
}
