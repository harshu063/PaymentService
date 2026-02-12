package com.example.paymentservice.adapters.paymentGatewayAdapters;

public interface PaymentGatewayAdapter {
    String createPaymentLink(Long price) throws Exception ;
}
