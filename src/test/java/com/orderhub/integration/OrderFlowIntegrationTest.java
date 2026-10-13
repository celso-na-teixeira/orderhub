package com.orderhub.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
                .andExpect(status().isCreated())
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
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.subtotal").value(50.46));
    }

    @Test
    void replacingItemsSwapsTheOrderLinesAndKeepsTheProducts() throws Exception {
        String token = tokenFor("alice@example.com");

        String created = mockMvc.perform(post("/api/orders")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [{"productId": 7, "quantity": 2}]}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long orderId = json(created).get("id").asLong();

        mockMvc.perform(put("/api/orders/{id}/items", orderId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [{"productId": 6, "quantity": 1}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(6));

        mockMvc.perform(get("/api/products/7"))
                .andExpect(status().isOk());
    }

    @Test
    void customerCannotReadAnotherCustomersOrder() throws Exception {
        String bob = tokenFor("bob@example.com");

        // order 1 belongs to Alice
        mockMvc.perform(get("/api/orders/1").header("Authorization", bob))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotReplaceItemsOnAnotherCustomersOrder() throws Exception {
        String carol = tokenFor("carol@example.com");

        String created = mockMvc.perform(post("/api/orders")
                        .header("Authorization", carol)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [{"productId": 7, "quantity": 2}]}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long orderId = json(created).get("id").asLong();

        mockMvc.perform(put("/api/orders/{id}/items", orderId)
                        .header("Authorization", tokenFor("alice@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [{"productId": 6, "quantity": 1}]}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/orders/{id}", orderId).header("Authorization", carol))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(7))
                .andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    @Test
    void customerCannotReadAnotherCustomersOrderHistory() throws Exception {
        // order 1 belongs to Alice
        mockMvc.perform(get("/api/orders/1/history").header("Authorization", tokenFor("bob@example.com")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/orders/1/history").header("Authorization", tokenFor("alice@example.com")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/orders/1/history").header("Authorization", tokenFor("staff@orderhub.io")))
                .andExpect(status().isOk());
    }

    @Test
    void customerCannotListAnotherCustomersOrders() throws Exception {
        String bob = tokenFor("bob@example.com");
        // order 1 belongs to Alice
        long alicesCustomerId = json(mockMvc.perform(get("/api/orders/1").header("Authorization", tokenFor("alice@example.com")))
                .andReturn().getResponse().getContentAsString()).get("customerId").asLong();

        mockMvc.perform(get("/api/orders").param("customerId", String.valueOf(alicesCustomerId)).param("page", "0")
                        .header("Authorization", bob))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/orders").param("page", "0").header("Authorization", bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.customerId == " + alicesCustomerId + ")]").isEmpty());

        mockMvc.perform(get("/api/orders").param("customerId", String.valueOf(alicesCustomerId)).param("page", "0")
                        .header("Authorization", tokenFor("staff@orderhub.io")))
                .andExpect(status().isOk());
    }

    @Test
    void refundAddsARefundedPaymentAndLeavesTheChargeUntouched() throws Exception {
        String token = tokenFor("alice@example.com");
        String staff = tokenFor("staff@orderhub.io");

        String created = mockMvc.perform(post("/api/orders")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [{"productId": 7, "quantity": 1}]}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long orderId = json(created).get("id").asLong();

        mockMvc.perform(post("/api/orders/{id}/payments", orderId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 7.99, \"cardToken\": \"tok_visa\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/orders/{id}/refund", orderId)
                        .header("Authorization", staff)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Customer changed their mind\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));

        mockMvc.perform(get("/api/orders/{id}/payments", orderId).header("Authorization", staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.status == 'SUCCEEDED')].amount").value(7.99))
                .andExpect(jsonPath("$[?(@.status == 'REFUNDED')].amount").value(7.99));
    }

    @Test
    void declinedPaymentIsRecordedAndLeavesTheOrderPending() throws Exception {
        String token = tokenFor("alice@example.com");

        String created = mockMvc.perform(post("/api/orders")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [{"productId": 7, "quantity": 1}]}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long orderId = json(created).get("id").asLong();

        mockMvc.perform(post("/api/orders/{id}/payments", orderId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 7.99, \"cardToken\": \"tok_decline\"}"))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(get("/api/orders/{id}/payments", orderId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("FAILED"));

        mockMvc.perform(get("/api/orders/{id}", orderId).header("Authorization", token))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void orderCannotBePlacedWithAZeroQuantityLine() throws Exception {
        String token = tokenFor("alice@example.com");

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [{"productId": 7, "quantity": 0}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("quantity")));
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
