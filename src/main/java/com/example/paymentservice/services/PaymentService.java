package com.example.paymentservice.services;

import com.example.paymentservice.adapters.paymentGatewayAdapters.PaymentGatewayAdapter;
import com.example.paymentservice.adapters.paymentGatewayAdapters.PaymentGatewayStrategy;
import com.example.paymentservice.models.PaymentGateway;
import org.springframework.stereotype.Service;

@Service

public class PaymentService {
    private final PaymentGatewayStrategy paymentGatewayStrategy;
    public PaymentService(PaymentGatewayStrategy paymentGatewayStrategy) {
        this.paymentGatewayStrategy = paymentGatewayStrategy;
    }
    public String createPaymentLink(Long orderId){

        //1. Get the order details from the order service
        // restTemplate.getForObject("http://order-service/orders/{orderId}",   Order.class, orderId)

        Long price = 123L;
        //2. Get a payment GAteway based upon a 'strategy'
        PaymentGatewayAdapter paymentGatewayAdapter = paymentGatewayStrategy.getPaymentGateway();
        //3. call the payment pg to create a payment link
        String url = "";
        try{
            url = paymentGatewayAdapter.createPaymentLink(price);
        }
        catch (Exception e){
            e.printStackTrace();
        }


        // 4. Return the payment Link
        return url;

    }

}
