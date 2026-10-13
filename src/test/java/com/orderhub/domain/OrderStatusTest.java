package com.orderhub.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @Test
    void pendingOrderCanBePaid() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.PAID)).isTrue();
    }

    @Test
    void paidOrderCanBeShipped() {
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.SHIPPED)).isTrue();
    }

    @Test
    void shippedOrderCannotGoBackToPending() {
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.PENDING)).isFalse();
    }

    @Test
    void pendingAndPaidOrdersCanBeCancelled() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    void shippedAndDeliveredOrdersCannotBeCancelled() {
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
    }

    @Test
    void stepsOfTheLifecycleCannotBeSkipped() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.SHIPPED)).isFalse();
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.DELIVERED)).isFalse();
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.REFUNDED)).isFalse();
    }

    @Test
    void terminalStatusesAllowNoFurtherTransition() {
        for (OrderStatus next : OrderStatus.values()) {
            assertThat(OrderStatus.CANCELLED.canTransitionTo(next)).isFalse();
            assertThat(OrderStatus.REFUNDED.canTransitionTo(next)).isFalse();
        }
    }

    @Test
    void statusCannotTransitionToItself() {
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(status.canTransitionTo(status)).isFalse();
        }
    }

    @Test
    void cancelledAndRefundedAreTerminal() {
        assertThat(OrderStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(OrderStatus.REFUNDED.isTerminal()).isTrue();
        assertThat(OrderStatus.PENDING.isTerminal()).isFalse();
    }
}
