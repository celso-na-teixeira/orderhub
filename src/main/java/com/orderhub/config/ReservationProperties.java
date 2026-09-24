package com.orderhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.reservation")
public class ReservationProperties {

    /**
     * How long stock stays reserved for an unpaid order.
     */
    private Duration ttl = Duration.ofHours(24);

    /**
     * Interval between runs of the reservation clean-up job, in milliseconds.
     */
    private long cleanupIntervalMs = 60_000;

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public long getCleanupIntervalMs() {
        return cleanupIntervalMs;
    }

    public void setCleanupIntervalMs(long cleanupIntervalMs) {
        this.cleanupIntervalMs = cleanupIntervalMs;
    }
}
