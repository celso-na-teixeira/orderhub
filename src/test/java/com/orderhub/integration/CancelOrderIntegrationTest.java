package com.orderhub.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CancelOrderIntegrationTest extends AbstractIntegrationTest {

    private static final String ONE_PEN_PACK = """
            {"items": [{"productId": 7, "quantity": 1}]}
            """;

    @Test
    void customerCanCancelTheirOwnPendingOrder() throws Exception {
        String token = tokenFor("alice@example.com");
        long orderId = placeOrder(token, ONE_PEN_PACK).get("id").asLong();

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/api/orders/{id}", orderId).header("Authorization", token))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void customerCannotCancelAnotherCustomersOrder() throws Exception {
        String carol = tokenFor("carol@example.com");
        long orderId = placeOrder(carol, ONE_PEN_PACK).get("id").asLong();

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId).header("Authorization", tokenFor("alice@example.com")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/orders/{id}", orderId).header("Authorization", carol))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void staffCanCancelACustomersOrder() throws Exception {
        long orderId = placeOrder(tokenFor("alice@example.com"), ONE_PEN_PACK).get("id").asLong();

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId).header("Authorization", tokenFor("staff@orderhub.io")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancellingAPendingOrderReleasesTheReservedStock() throws Exception {
        String staff = tokenFor("staff@orderhub.io");
        long productId = createProductWithStock("TST-7400", 5);
        long orderId = placeOrder(tokenFor("alice@example.com"),
                "{\"items\": [{\"productId\": " + productId + ", \"quantity\": 2}]}").get("id").asLong();

        mockMvc.perform(get("/api/inventory/{productId}", productId).header("Authorization", staff))
                .andExpect(jsonPath("$.quantityReserved").value(2));

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId).header("Authorization", staff))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/inventory/{productId}", productId).header("Authorization", staff))
                .andExpect(jsonPath("$.quantityOnHand").value(5))
                .andExpect(jsonPath("$.quantityReserved").value(0));
    }

    @Test
    void cancellingAPaidOrderPutsTheStockBackOnHand() throws Exception {
        String staff = tokenFor("staff@orderhub.io");
        long productId = createProductWithStock("TST-7401", 5);
        long orderId = placeAndPay(tokenFor("alice@example.com"),
                "{\"items\": [{\"productId\": " + productId + ", \"quantity\": 2}]}");

        mockMvc.perform(get("/api/inventory/{productId}", productId).header("Authorization", staff))
                .andExpect(jsonPath("$.quantityOnHand").value(3));

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId).header("Authorization", staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/api/inventory/{productId}", productId).header("Authorization", staff))
                .andExpect(jsonPath("$.quantityOnHand").value(5))
                .andExpect(jsonPath("$.quantityReserved").value(0));
    }

    @Test
    void shippedOrderCannotBeCancelled() throws Exception {
        String token = tokenFor("alice@example.com");
        String staff = tokenFor("staff@orderhub.io");
        long orderId = placeAndPay(token, ONE_PEN_PACK);

        mockMvc.perform(patch("/api/orders/{id}/status", orderId)
                        .header("Authorization", staff)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"SHIPPED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId).header("Authorization", staff))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/orders/{id}", orderId).header("Authorization", staff))
                .andExpect(jsonPath("$.status").value("SHIPPED"));
    }

    @Test
    void cancelledOrderCannotBeCancelledAgain() throws Exception {
        String staff = tokenFor("staff@orderhub.io");
        long orderId = placeOrder(tokenFor("alice@example.com"), ONE_PEN_PACK).get("id").asLong();

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId).header("Authorization", staff))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId).header("Authorization", staff))
                .andExpect(status().isConflict());
    }

    @Test
    void cancellingAnUnknownOrderReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/orders/{id}/cancel", 999_999).header("Authorization", tokenFor("staff@orderhub.io")))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelIsNotAvailableAsAGet() throws Exception {
        String staff = tokenFor("staff@orderhub.io");
        long orderId = placeOrder(tokenFor("alice@example.com"), ONE_PEN_PACK).get("id").asLong();

        mockMvc.perform(get("/api/orders/{id}/cancel", orderId).header("Authorization", staff))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(get("/api/orders/{id}", orderId).header("Authorization", staff))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    private long createProductWithStock(String sku, int stock) throws Exception {
        String created = mockMvc.perform(post("/api/products")
                        .header("Authorization", tokenFor("admin@orderhub.io"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\": \"" + sku + "\", \"name\": \"Cancel test\", \"category\": \"OFFICE\", "
                                + "\"price\": 4.99, \"initialStock\": " + stock + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json(created).get("id").asLong();
    }

    private JsonNode placeOrder(String token, String body) throws Exception {
        String created = mockMvc.perform(post("/api/orders")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json(created);
    }

    private long placeAndPay(String token, String body) throws Exception {
        JsonNode order = placeOrder(token, body);
        long orderId = order.get("id").asLong();
        mockMvc.perform(post("/api/orders/{id}/payments", orderId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": " + order.get("total").decimalValue().toPlainString()
                                + ", \"cardToken\": \"tok_visa\"}"))
                .andExpect(status().isCreated());
        return orderId;
    }
}
