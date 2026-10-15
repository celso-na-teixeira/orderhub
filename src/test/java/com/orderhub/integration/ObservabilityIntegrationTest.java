package com.orderhub.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the real servers, so the actuator is checked on its own port, the way the cluster reaches it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
@AutoConfigureObservability
class ObservabilityIntegrationTest extends AbstractIntegrationTest {

    private static final String ONE_PEN_PACK = """
            {"items": [{"productId": 7, "quantity": 1}]}
            """;

    @LocalServerPort
    private int serverPort;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private TestRestTemplate rest;

    @Test
    void probesAreUpOnTheManagementPort() {
        assertThat(management("/actuator/health/liveness").getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> readiness = management("/actuator/health/readiness");
        assertThat(readiness.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readiness.getBody()).contains("\"UP\"").doesNotContain("components");
    }

    @Test
    void actuatorIsNotReachableThroughTheApplicationPort() {
        ResponseEntity<String> response = rest.getForEntity("http://localhost:" + serverPort + "/actuator/health", String.class);
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.OK);
    }

    @Test
    void sensitiveEndpointsAreNotExposed() {
        for (String path : new String[] {"/actuator/env", "/actuator/heapdump", "/actuator/loggers", "/actuator/configprops"}) {
            assertThat(management(path).getStatusCode()).as(path).isNotEqualTo(HttpStatus.OK);
        }
    }

    @Test
    void prometheusExposesHttpJdbcAndBusinessMetrics() throws Exception {
        placeOrder(tokenFor("alice@example.com"));

        ResponseEntity<String> scrape = management("/actuator/prometheus");

        assertThat(scrape.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scrape.getBody())
                .contains("orderhub_orders_placed_total{application=\"orderhub\"")
                .contains("http_server_requests_seconds_count")
                .contains("hikaricp_connections_active")
                .contains("jdbc_query");
    }

    @Test
    void auditHistoryIsRecordedWithTheOrder() throws Exception {
        String token = tokenFor("alice@example.com");
        long orderId = placeOrder(token).get("id").asLong();

        mockMvc.perform(get("/api/orders/{id}/history", orderId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("CREATED"))
                .andExpect(jsonPath("$[0].actor").value("alice@example.com"));
    }

    @Test
    void wellFormedRequestIdIsKeptAndMalformedOneIsReplaced() throws Exception {
        mockMvc.perform(get("/api/products").header("X-Request-Id", "checkout-7f3a.1"))
                .andExpect(header().string("X-Request-Id", "checkout-7f3a.1"));

        String forged = mockMvc.perform(get("/api/products").header("X-Request-Id", "x\nFAKE LOG LINE"))
                .andReturn().getResponse().getHeader("X-Request-Id");
        assertThat(forged).matches("[0-9a-f-]{36}");
    }

    private ResponseEntity<String> management(String path) {
        return rest.getForEntity("http://localhost:" + managementPort + path, String.class);
    }

    private JsonNode placeOrder(String token) throws Exception {
        String created = mockMvc.perform(post("/api/orders")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ONE_PEN_PACK))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json(created);
    }
}
