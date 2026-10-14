package com.orderhub.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// a misspelled key fails startup instead of silently leaving the default in place
@ConfigurationProperties(prefix = "app.reservation", ignoreUnknownFields = false)
@Getter
@Setter
public class ReservationProperties {

    /**
     * How long stock stays reserved for an unpaid order (BR-30).
     */
    private Duration ttl = Duration.ofMinutes(15);

    /**
     * Interval between runs of the reservation clean-up job, in milliseconds.
     */
    private long cleanupIntervalMs = 60_000;
}
