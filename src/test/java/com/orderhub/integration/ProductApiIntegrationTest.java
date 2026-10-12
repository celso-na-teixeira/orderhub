package com.orderhub.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

    @Test
    void patchChangesOnlyTheFieldsThatAreSent() throws Exception {
        String admin = tokenFor("admin@orderhub.io");

        String created = mockMvc.perform(post("/api/products")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku": "TST-7001", "name": "Desk Lamp", "category": "OFFICE", "price": 19.99, "initialStock": 5}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(19.99));

        mockMvc.perform(patch("/api/products/{id}", id)
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"price": 24.50}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(24.50))
                .andExpect(jsonPath("$.name").value("Desk Lamp"))
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(24.50))
                .andExpect(jsonPath("$.name").value("Desk Lamp"))
                .andExpect(jsonPath("$.availableQuantity").value(5));
    }
}
