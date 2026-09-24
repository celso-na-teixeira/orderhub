package com.orderhub.repository;

import com.orderhub.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByActiveTrue(Pageable pageable);

    Page<Product> findByNameContainingAndCategoryAndActiveTrue(String name, String category, Pageable pageable);

    List<Product> findByCategory(String category);

    boolean existsBySku(String sku);
}
