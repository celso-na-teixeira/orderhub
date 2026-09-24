package com.orderhub.service;

import com.orderhub.domain.InventoryItem;
import com.orderhub.domain.Product;
import com.orderhub.exception.InsufficientStockException;
import com.orderhub.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        item = new InventoryItem(new Product(), 10);
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
        item.setQuantityReserved(8);

        assertThatThrownBy(() -> inventoryService.reserve(1L, 3))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void commitReducesOnHandAndReserved() {
        item.setQuantityReserved(4);

        inventoryService.commit(1L, 4);

        assertThat(item.getQuantityOnHand()).isEqualTo(6);
        assertThat(item.getQuantityReserved()).isZero();
    }

    @Test
    void releaseReturnsStockToThePool() {
        inventoryService.reserve(1L, 2);

        inventoryService.release(1L, 2);

        assertThat(item.getQuantityReserved()).isZero();
        assertThat(item.getQuantityOnHand()).isEqualTo(12);
    }
}
