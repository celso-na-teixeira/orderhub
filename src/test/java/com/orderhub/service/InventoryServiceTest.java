package com.orderhub.service;

import com.orderhub.domain.InventoryItem;
import com.orderhub.domain.Product;
import com.orderhub.exception.BusinessRuleException;
import com.orderhub.exception.InsufficientStockException;
import com.orderhub.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private InventoryItem item;

    @BeforeEach
    void setUp() {
        item = new InventoryItem(new Product("TST-0001", "Test product", "TEST", new BigDecimal("1.00")), 10);
        when(inventoryRepository.findByProductId(1L)).thenReturn(Optional.of(item));
    }

    @Test
    void reserveIncreasesReservedQuantity() {
        inventoryService.reserve(1L, 3);

        assertThat(item.getQuantityReserved()).isEqualTo(3);
        assertThat(item.getQuantityOnHand()).isEqualTo(10);
        assertThat(item.getAvailableQuantity()).isEqualTo(7);
    }

    @Test
    void reserveFailsWhenNotEnoughAvailable() {
        item.reserve(8);

        assertThatThrownBy(() -> inventoryService.reserve(1L, 3))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void commitReducesOnHandAndReserved() {
        item.reserve(4);

        inventoryService.commit(1L, 4);

        assertThat(item.getQuantityOnHand()).isEqualTo(6);
        assertThat(item.getQuantityReserved()).isZero();
    }

    @Test
    void releaseReturnsStockToThePool() {
        inventoryService.reserve(1L, 2);

        inventoryService.release(1L, 2);

        assertThat(item.getQuantityReserved()).isZero();
        assertThat(item.getQuantityOnHand()).isEqualTo(10);
        assertThat(item.getAvailableQuantity()).isEqualTo(10);
    }

    @Test
    void releaseCannotFreeMoreThanIsReserved() {
        inventoryService.reserve(1L, 2);

        assertThatThrownBy(() -> inventoryService.release(1L, 3))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void restockPutsCommittedStockBackOnHand() {
        inventoryService.reserve(1L, 4);
        inventoryService.commit(1L, 4);

        inventoryService.restock(1L, 4);

        assertThat(item.getQuantityOnHand()).isEqualTo(10);
        assertThat(item.getQuantityReserved()).isZero();
    }

    @Test
    void adjustCannotSetOnHandBelowReserved() {
        inventoryService.reserve(1L, 6);

        assertThatThrownBy(() -> inventoryService.adjust(1L, 5, "stock count"))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(item.getQuantityOnHand()).isEqualTo(10);
    }
}
