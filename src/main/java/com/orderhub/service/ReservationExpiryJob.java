package com.orderhub.service;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Cancels pending orders whose reservations have expired and releases their stock (BR-31).
 * Each order is expired in its own transaction: an order that is paid or edited at the same moment
 * fails on its version check and is simply retried on the next run.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ReservationExpiryJob {

    private final ReservationService reservationService;
    private final OrderService orderService;
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedDelayString = "${app.reservation.cleanup-interval-ms:60000}")
    public void expireStaleReservations() {
        Set<Long> orderIds = reservationService.findOrdersWithStaleReservations();
        int expired = 0;
        int failed = 0;
        for (Long orderId : orderIds) {
            try {
                orderService.expireOrder(orderId);
                expired++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("Could not expire order {}, will retry on the next run: {}", orderId, e.getMessage());
            }
        }
        meterRegistry.counter("orderhub.orders.expired").increment(expired);
        // a lasting non-zero rate means some orders are stuck pending with stock held
        meterRegistry.counter("orderhub.orders.expiry.failures").increment(failed);
        if (expired > 0) {
            log.info("Expired reservations of {} orders", expired);
        }
    }
}
