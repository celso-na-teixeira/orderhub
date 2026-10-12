package com.orderhub.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DiscountApiIntegrationTest extends AbstractIntegrationTest {

    @Test
    void duplicateCodeIsRejectedIgnoringCase() throws Exception {
        mockMvc.perform(post("/api/discounts")
                        .header("Authorization", tokenFor("admin@orderhub.io"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "spring5", "type": "FIXED_AMOUNT", "value": 5}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void customersCannotListDiscounts() throws Exception {
        mockMvc.perform(get("/api/discounts").header("Authorization", tokenFor("alice@example.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void codeThatHasReachedItsUsageLimitCannotBeUsed() throws Exception {
        // LIMITED20 is seeded with 3 of 3 uses taken
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", tokenFor("alice@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [{"productId": 7, "quantity": 2}], "discountCode": "LIMITED20"}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void orderWithAValidCodeIsDiscounted() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", tokenFor("alice@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [{"productId": 7, "quantity": 2}], "discountCode": "welcome10"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.discountAmount").value(1.60))
                .andExpect(jsonPath("$.total").value(14.38));
    }
}
