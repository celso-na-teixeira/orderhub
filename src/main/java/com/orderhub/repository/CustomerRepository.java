package com.orderhub.repository;

import com.orderhub.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    // explicit query: Customer.getUserId() would otherwise make Spring Data look for a "userId" column
    @Query("select c from Customer c where c.user.id = :userId")
    Optional<Customer> findByUserId(@Param("userId") Long userId);
}
