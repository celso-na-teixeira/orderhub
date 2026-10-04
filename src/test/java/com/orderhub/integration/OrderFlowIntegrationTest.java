package com.orderhub.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

class OrderFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    void customerCanPlaceAndPayForAnOrder() throws Exception {
        String token = tokenFor("alice@example.com");

        String created = mockMvc.perform(post("/api/orders")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [{"productId": 7, "quantity": 2}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        JsonNode order = json(created);
        long orderId = order.get("id").asLong();
        BigDecimal total = order.get("total").decimalValue();
        assertThat(total).isEqualByComparingTo("15.98");

        mockMvc.perform(post("/api/orders/{id}/payments", orderId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": " + total.toPlainString() + ", \"cardToken\": \"tok_visa\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        mockMvc.perform(get("/api/orders/{id}", orderId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void orderWithSeveralProductsKeepsEveryLine() throws Exception {
        String token = tokenFor("bob@example.com");

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [
                                  {"productId": 6, "quantity": 1},
                                  {"productId": 18, "quantity": 3}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.subtotal").value(50.46));
    }

    @Test
    void customerCannotReadAnotherCustomersOrder() throws Exception {
        String bob = tokenFor("bob@example.com");

        // order 1 belongs to Alice
        mockMvc.perform(get("/api/orders/1").header("Authorization", bob))
                .andExpect(status().isForbidden());
    }

    @Test
    void orderCannotBePlacedWithoutItems() throws Exception {
        String token = tokenFor("alice@example.com");
        
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": []}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("items")));
    }
}
