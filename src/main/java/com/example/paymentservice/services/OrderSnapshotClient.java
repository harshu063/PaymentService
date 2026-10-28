package com.example.paymentservice.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class OrderSnapshotClient {
    private final RestClient restClient;

    public OrderSnapshotClient(RestClient.Builder builder,
                               @Value("${services.order.base-url}") String orderBaseUrl) {
        this.restClient = builder.baseUrl(orderBaseUrl).build();
    }

    public OrderSnapshot get(UUID orderId, String authorizationHeader) {
        return restClient.get()
                .uri("/orders/{orderId}", orderId)
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .retrieve()
                .body(OrderSnapshot.class);
    }

    public record OrderSnapshot(UUID orderId, String status, long totalMinor, String currency, long version) {
    }
}
