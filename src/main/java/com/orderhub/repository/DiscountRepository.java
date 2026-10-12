package com.orderhub.repository;

import com.orderhub.domain.Discount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DiscountRepository extends JpaRepository<Discount, Long> {

    Optional<Discount> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    /**
     * Counts one more use of the discount, unless it has no uses left.
     * The check and the increment happen in a single statement, so concurrent orders cannot exceed maxUses.
     *
     * @return 1 if the use was counted, 0 if the discount is already at its limit
     */
    @Modifying
    @Query("update Discount d set d.usedCount = d.usedCount + 1 "
            + "where d.id = :id and (d.maxUses is null or d.usedCount < d.maxUses)")
    int incrementUsage(@Param("id") Long id);
}
