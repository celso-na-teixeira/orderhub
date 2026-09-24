package com.orderhub.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductApiIntegrationTest extends AbstractIntegrationTest {

    @Test
    void anonymousUsersCanBrowseTheCatalogue() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isNotEmpty());
    }

    @Test
    void catalogueCanBeFilteredByCategory() throws Exception {
        mockMvc.perform(get("/api/products").param("category", "KITCHEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5));
    }

    @Test
    void customersCannotCreateProducts() throws Exception {
        String token = tokenFor("carol@example.com");

        mockMvc.perform(post("/api/products")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku": "TST-9999", "name": "Test", "category": "OFFICE", "price": 1.00, "initialStock": 1}
                                """))
                .andExpect(status().isForbidden());
    }
}
