package com.example.paymentservice;

import com.example.paymentservice.adapters.paymentGatewayAdapters.PaymentGatewayAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

@SpringBootApplication
@EnableScheduling
public class PaymentServiceApplication {

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @ConditionalOnProperty(name = "payments.stripe.enabled", havingValue = "false")
    PaymentGatewayAdapter disabledPaymentGatewayAdapter() {
        return new PaymentGatewayAdapter() {
            @Override
            public ProviderCheckout createCheckout(java.util.UUID paymentId, java.util.UUID orderId,
                                                   long amountMinor, String currency, String idempotencyKey) {
                throw new IllegalStateException("No payment gateway is enabled");
            }

            @Override
            public VerifiedWebhook verifyWebhook(String payload, String signature) {
                throw new IllegalStateException("No payment gateway is enabled");
            }
        };
    }

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }

}
