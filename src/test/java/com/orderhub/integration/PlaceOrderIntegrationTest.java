package com.orderhub.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlaceOrderIntegrationTest extends AbstractIntegrationTest {

    private static final String ONE_PEN_PACK = """
            {"items": [{"productId": 7, "quantity": 1}]}
            """;

    @Test
    void repeatedRequestWithTheSameKeyReturnsTheOriginalOrder() throws Exception {
        String token = tokenFor("alice@example.com");

        long first = placeOrder(token, "key-repeat", ONE_PEN_PACK);
        long second = placeOrder(token, "key-repeat", ONE_PEN_PACK);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void sameKeyFromAnotherCustomerCreatesTheirOwnOrder() throws Exception {
        long alices = placeOrder(tokenFor("alice@example.com"), "key-shared", ONE_PEN_PACK);
        long bobs = placeOrder(tokenFor("bob@example.com"), "key-shared", ONE_PEN_PACK);

        assertThat(bobs).isNotEqualTo(alices);
    }

    @Test
    void consecutiveOrdersGetDifferentOrderNumbers() throws Exception {
        String token = tokenFor("alice@example.com");

        String first = orderNumber(token, placeOrder(token, null, ONE_PEN_PACK));
        String second = orderNumber(token, placeOrder(token, null, ONE_PEN_PACK));

        assertThat(first).matches("ORD-\\d{8}-\\d{5}");
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void linesForTheSameProductAreMerged() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", tokenFor("alice@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [{"productId": 7, "quantity": 1}, {"productId": 7, "quantity": 2}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(3));
    }

    @Test
    void lastUnitCanOnlyBeOrderedOnceUnderConcurrentCheckouts() throws Exception {
        String created = mockMvc.perform(post("/api/products")
                        .header("Authorization", tokenFor("admin@orderhub.io"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku": "TST-7100", "name": "Last One", "category": "OFFICE", "price": 5.00, "initialStock": 1}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long productId = json(created).get("id").asLong();
        String body = "{\"items\": [{\"productId\": " + productId + ", \"quantity\": 1}]}";

        List<String> tokens = List.of(tokenFor("alice@example.com"), tokenFor("bob@example.com"));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(tokens.size());
        try {
            List<Future<Integer>> results = tokens.stream()
                    .map(token -> pool.submit((Callable<Integer>) () -> {
                        start.await();
                        return mockMvc.perform(post("/api/orders")
                                        .header("Authorization", token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                                .andReturn().getResponse().getStatus();
                    }))
                    .toList();
            start.countDown();

            List<Integer> statuses = List.of(results.get(0).get(), results.get(1).get());
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdownNow();
        }

        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(jsonPath("$.availableQuantity").value(0));
    }

    @Test
    void replacingItemsMovesTheStockReservationToTheNewProducts() throws Exception {
        String admin = tokenFor("admin@orderhub.io");
        long productA = createProductWithStock(admin, "TST-7300", 5);
        long productB = createProductWithStock(admin, "TST-7301", 5);
        String token = tokenFor("alice@example.com");

        String created = mockMvc.perform(post("/api/orders")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\": [{\"productId\": " + productA + ", \"quantity\": 2}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long orderId = json(created).get("id").asLong();

        mockMvc.perform(get("/api/products/{id}", productA))
                .andExpect(jsonPath("$.availableQuantity").value(3));

        mockMvc.perform(put("/api/orders/{id}/items", orderId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\": [{\"productId\": " + productB + ", \"quantity\": 1}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(productB));

        // product A's reservation was released...
        mockMvc.perform(get("/api/products/{id}", productA))
                .andExpect(jsonPath("$.availableQuantity").value(5));
        // ...and product B is now reserved for this order
        mockMvc.perform(get("/api/products/{id}", productB))
                .andExpect(jsonPath("$.availableQuantity").value(4));
    }

    private long createProductWithStock(String adminToken, String sku, int stock) throws Exception {
        String created = mockMvc.perform(post("/api/products")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\": \"" + sku + "\", \"name\": \"Replace test\", \"category\": \"OFFICE\", "
                                + "\"price\": 5.00, \"initialStock\": " + stock + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json(created).get("id").asLong();
    }

    private long placeOrder(String token, String idempotencyKey, String body) throws Exception {
        var request = post("/api/orders")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (idempotencyKey != null) {
            request = request.header("Idempotency-Key", idempotencyKey);
        }
        String created = mockMvc.perform(request)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json(created).get("id").asLong();
    }

    private String orderNumber(String token, long orderId) throws Exception {
        String order = mockMvc.perform(get("/api/orders/{id}", orderId).header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json(order).get("orderNumber").asText();
    }
}
