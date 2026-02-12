package com.example.paymentservice.adapters.paymentGatewayAdapters;

import com.example.paymentservice.adapters.paymentGatewayAdapters.Stripe.StripePaymentGatewayAdapter;
import com.example.paymentservice.adapters.paymentGatewayAdapters.razorpay.RazorpayPaymentGatewayAdapter;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class PaymentGatewayStrategy {
    private static Random random = new Random();
    private final StripePaymentGatewayAdapter stripePaymentGatewayAdapter;
    public PaymentGatewayStrategy(StripePaymentGatewayAdapter stripePaymentGatewayAdapter) {
        this.stripePaymentGatewayAdapter = stripePaymentGatewayAdapter;
    }
    public PaymentGatewayAdapter getPaymentGateway(){

//        int isEven = random.nextInt(100);
        return stripePaymentGatewayAdapter;
//        if(isEven == 0){
//            return new StripePaymentGatewayAdapter();
//        }else{
//            return new RazorpayPaymentGatewayAdapter();
//        }
    }
}
