package com.orderhub.integration;

import com.orderhub.domain.Role;
import com.orderhub.domain.User;
import com.orderhub.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JwtAuthenticationIntegrationTest extends AbstractIntegrationTest {

    @Value("${app.jwt.secret}")
    private String secret;

    @Test
    void expiredTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/orders").header("Authorization", "Bearer " + expiredTokenFor("alice@example.com")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/orders").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicEndpointsStillAnswerWhenTheTokenHasExpired() throws Exception {
        mockMvc.perform(get("/api/products").header("Authorization", "Bearer " + expiredTokenFor("alice@example.com")))
                .andExpect(status().isOk());
    }

    private String expiredTokenFor(String email) {
        User user = new User();
        user.setId(3L);
        user.setEmail(email);
        user.setRole(Role.CUSTOMER);
        return new JwtService(secret, -1).generateToken(user);
    }
}
