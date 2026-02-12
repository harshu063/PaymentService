package com.example.paymentservice.adapters.paymentGatewayAdapters.Stripe;

import com.stripe.Stripe;
import com.stripe.StripeClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfiguration {
    @Value("${apikeys.stripe}")
    private String stripeApiKey;

    @Bean
    public StripeClient getStripeClient(){
        Stripe.apiKey = stripeApiKey;
        return new StripeClient(stripeApiKey);
    }

}
