package com.orderhub.integration;

import com.orderhub.exception.InsufficientStockException;
import com.orderhub.repository.ProductRepository;
import com.orderhub.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InventoryLockingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void reserveSeesStockTakenByAnotherTransactionEvenIfTheItemWasAlreadyLoaded() throws Exception {
        long productId = createProductWithStock("TST-7200", 2);
        TransactionTemplate thisRequest = new TransactionTemplate(transactionManager);
        TransactionTemplate otherRequest = new TransactionTemplate(transactionManager);
        otherRequest.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        thisRequest.executeWithoutResult(status -> {
            // this request reads the stock while both units are still available...
            int seen = productRepository.findById(productId).orElseThrow().getInventory().getAvailableQuantity();
            assertThat(seen).isEqualTo(2);

            // ...then another order takes both units and commits
            otherRequest.executeWithoutResult(other -> inventoryService.reserve(productId, 2));

            assertThatThrownBy(() -> inventoryService.reserve(productId, 1))
                    .isInstanceOf(InsufficientStockException.class);
            // the failed reserve has already marked this transaction for rollback
            status.setRollbackOnly();
        });
    }

    private long createProductWithStock(String sku, int stock) throws Exception {
        String created = mockMvc.perform(post("/api/products")
                        .header("Authorization", tokenFor("admin@orderhub.io"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\": \"" + sku + "\", \"name\": \"Lock test\", \"category\": \"OFFICE\", "
                                + "\"price\": 5.00, \"initialStock\": " + stock + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json(created).get("id").asLong();
    }
}
