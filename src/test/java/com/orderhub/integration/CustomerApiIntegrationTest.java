package com.orderhub.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CustomerApiIntegrationTest extends AbstractIntegrationTest {

    @Test
    void profileUpdateIsSaved() throws Exception {
        String token = tokenFor("carol@example.com");

        String me = mockMvc.perform(get("/api/customers/me").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long id = json(me).get("id").asLong();

        mockMvc.perform(put("/api/customers/{id}", id)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Carol Updated", "email": "carol@example.com"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Carol Updated"));

        mockMvc.perform(get("/api/customers/me").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Carol Updated"));
    }
}
