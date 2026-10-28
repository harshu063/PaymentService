package com.example.paymentservice.adapters.paymentGatewayAdapters.Stripe;

import com.example.paymentservice.adapters.paymentGatewayAdapters.PaymentGatewayAdapter;
import com.stripe.StripeClient;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "payments.stripe.enabled", havingValue = "true", matchIfMissing = true)
public class StripePaymentGatewayAdapter implements PaymentGatewayAdapter {
    private final StripeClient stripeClient;
    private final String webhookSecret;
    private final String successUrl;
    private final String cancelUrl;

    public StripePaymentGatewayAdapter(
            StripeClient stripeClient,
            @Value("${payments.stripe.webhook-secret}") String webhookSecret,
            @Value("${payments.stripe.success-url}") String successUrl,
            @Value("${payments.stripe.cancel-url}") String cancelUrl) {
        this.stripeClient = stripeClient;
        this.webhookSecret = webhookSecret;
        this.successUrl = successUrl;
        this.cancelUrl = cancelUrl;
    }

    @Override
    public ProviderCheckout createCheckout(UUID paymentId, UUID orderId, long amountMinor,
                                           String currency, String idempotencyKey) throws Exception {
        SessionCreateParams.LineItem.PriceData.ProductData product = SessionCreateParams.LineItem.PriceData.ProductData.builder()
                .setName("Order " + orderId)
                .build();
        SessionCreateParams.LineItem.PriceData price = SessionCreateParams.LineItem.PriceData.builder()
                .setCurrency(currency.toLowerCase(Locale.ROOT))
                .setUnitAmount(amountMinor)
                .setProductData(product)
                .build();
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .putMetadata("paymentId", paymentId.toString())
                .putMetadata("orderId", orderId.toString())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(price)
                        .build())
                .build();
        RequestOptions options = RequestOptions.builder().setIdempotencyKey(idempotencyKey).build();
        Session session = stripeClient.v1().checkout().sessions().create(params, options);
        return new ProviderCheckout(session.getId(), session.getUrl());
    }

    @Override
    public VerifiedWebhook verifyWebhook(String payload, String signature) throws SignatureVerificationException {
        Event event = Webhook.constructEvent(payload, signature, webhookSecret);
        StripeObject object = event.getDataObjectDeserializer().getObject()
                .orElseThrow(() -> new IllegalArgumentException("Webhook object could not be deserialized"));
        if (!(object instanceof Session session)) {
            return new VerifiedWebhook(event.getId(), event.getType(), null, null, Outcome.IGNORED);
        }
        Map<String, String> metadata = session.getMetadata();
        UUID paymentId = metadata == null || metadata.get("paymentId") == null
                ? null : UUID.fromString(metadata.get("paymentId"));
        Outcome outcome = switch (event.getType()) {
            case "checkout.session.completed", "checkout.session.async_payment_succeeded" -> Outcome.SUCCEEDED;
            case "checkout.session.async_payment_failed", "checkout.session.expired" -> Outcome.FAILED;
            default -> Outcome.IGNORED;
        };
        return new VerifiedWebhook(event.getId(), event.getType(), session.getId(), paymentId, outcome);
    }
}
