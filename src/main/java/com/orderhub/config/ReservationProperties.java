package com.orderhub.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.reservation")
@Getter
@Setter
public class ReservationProperties {

    /**
     * How long stock stays reserved for an unpaid order.
     */
    private Duration ttl = Duration.ofHours(24);

    /**
     * Interval between runs of the reservation clean-up job, in milliseconds.
     */
    private long cleanupIntervalMs = 60_000;
}
