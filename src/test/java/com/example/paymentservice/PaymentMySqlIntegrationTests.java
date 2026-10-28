package com.example.paymentservice;

import com.example.paymentservice.adapters.paymentGatewayAdapters.PaymentGatewayAdapter;
import com.example.paymentservice.repositories.PaymentAttemptRepository;
import com.example.paymentservice.services.OrderSnapshotClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {
        "payments.stripe.enabled=false",
        "security.jwt.jwk-set-uri=http://localhost/unused",
        "security.jwt.issuer=http://localhost:8081",
        "security.jwt.audience=platform-api"
})
class PaymentMySqlIntegrationTests {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("payment_service")
            .withUsername("payment_user")
            .withPassword("container-only-password");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @MockitoBean
    private PaymentGatewayAdapter gateway;
    @MockitoBean
    private OrderSnapshotClient orders;

    @Autowired
    private PaymentAttemptRepository attempts;

    @Test
    void flywayCreatesPaymentSchema() {
        assertThat(attempts.count()).isZero();
    }
}
