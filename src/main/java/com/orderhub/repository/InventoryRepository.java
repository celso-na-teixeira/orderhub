package com.orderhub.repository;

import com.orderhub.domain.InventoryItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<InventoryItem, Long> {

    Optional<InventoryItem> findByProductId(Long productId);

    /**
     * Loads the stock record and locks its row until the transaction ends, so concurrent stock changes cannot overlap.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.product.id = :productId")
    Optional<InventoryItem> findByProductIdForUpdate(@Param("productId") Long productId);
}
