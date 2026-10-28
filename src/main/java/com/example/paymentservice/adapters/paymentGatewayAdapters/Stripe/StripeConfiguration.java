package com.example.paymentservice.adapters.paymentGatewayAdapters.Stripe;

import com.stripe.Stripe;
import com.stripe.StripeClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "payments.stripe.enabled", havingValue = "true", matchIfMissing = true)
public class StripeConfiguration {
    @Value("${payments.stripe.api-key}")
    private String stripeApiKey;

    @Bean
    public StripeClient getStripeClient(){
        Stripe.apiKey = stripeApiKey;
        return new StripeClient(stripeApiKey);
    }

}
