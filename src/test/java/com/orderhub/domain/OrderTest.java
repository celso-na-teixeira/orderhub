package com.orderhub.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

public class OrderTest {

    @Test
    void addingTwoNewItemsKeepsBoth() {
        Order order = new Order();
        OrderItem item1 = new OrderItem(product("KIT-3002", "59.90"), 2, new BigDecimal("59.90"));
        OrderItem item2 = new OrderItem(product("KIT-3003", "39.90"), 1, new BigDecimal("39.90"));
        order.addItem(item1);
        order.addItem(item2);
        assertThat(order.getItems().size()).isEqualTo(2);
    }

    @Test 
    void unsavedItemsAreOnlyEqualToThemselves() {
        OrderItem item1 = new OrderItem(product("KIT-3002", "59.90"), 2, new BigDecimal("59.90"));
        OrderItem item2 = new OrderItem(product("KIT-3002", "59.90"), 2, new BigDecimal("59.90"));
        assertThat(item1).isEqualTo(item1).isNotEqualTo(item2);
        assertThat(item1.hashCode()).isEqualTo(item2.hashCode());
    }

    private static Product product(String sku, String price) {
        Product product = new Product();
        product.setSku(sku);
        product.setName(sku);
        product.setPrice(new BigDecimal(price));
        product.setActive(true);
        return product;
    }

}
