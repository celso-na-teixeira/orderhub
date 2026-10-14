package com.orderhub.repository;

import com.orderhub.domain.Order;
import com.orderhub.domain.OrderStatus;
import com.orderhub.dto.TopProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByCustomerIdAndIdempotencyKey(Long customerId, String idempotencyKey);

    Page<Order> findByCustomerIdAndStatus(Long customerId, OrderStatus status, Pageable pageable);

    @Query(value = """
            select distinct o from Order o
            left join fetch o.items i
            left join fetch i.product
            where o.customer.id = :customerId
            """,
           countQuery = "select count(o) from Order o where o.customer.id = :customerId")
    Page<Order> findWithItemsByCustomerId(@Param("customerId") Long customerId, Pageable pageable);

    List<Order> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    long countByStatus(OrderStatus status);

    /**
     * Best-selling products by revenue within a date range, counting only orders whose status
     * contributes to revenue (BR-80: PAID, SHIPPED or DELIVERED).
     */
    @Query("""
            select new com.orderhub.dto.TopProductResponse(p.id, p.sku, p.name, sum(i.quantity), sum(i.lineTotal))
            from Order o join o.items i join i.product p
            where o.status in :statuses and o.createdAt between :from and :to
            group by p.id, p.sku, p.name
            order by sum(i.lineTotal) desc
            """)
    List<TopProductResponse> topProducts(@Param("statuses") Collection<OrderStatus> statuses,
                                         @Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to,
                                         Pageable pageable);
}
