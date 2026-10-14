package com.orderhub.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled} jobs, such as the reservation expiry job (BR-31).
 */
@Configuration // makes Spring pick the class up during startup.
@EnableScheduling // tells Spring to look through every bean for @Scheduled methods and register them with a scheduler. 
public class SchedulingConfig {
}
