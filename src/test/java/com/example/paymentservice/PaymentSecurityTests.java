package com.example.paymentservice;

import com.example.paymentservice.adapters.paymentGatewayAdapters.PaymentGatewayAdapter;
import com.example.paymentservice.services.OrderSnapshotClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class PaymentSecurityTests {
    @jakarta.annotation.Resource
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentGatewayAdapter gateway;

    @MockitoBean
    private OrderSnapshotClient orders;

    @Test
    void endpointsRequireTheirDedicatedScopes() throws Exception {
        mockMvc.perform(post("/payments"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/payments").with(jwt()
                        .authorities(new SimpleGrantedAuthority("SCOPE_catalog.read"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/payments").with(jwt()
                        .authorities(new SimpleGrantedAuthority("SCOPE_payments.write"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/payments/3f38853d-3421-4a47-af17-b979f3150159").with(jwt()
                        .authorities(new SimpleGrantedAuthority("SCOPE_payments.write"))))
                .andExpect(status().isForbidden());
    }
}
