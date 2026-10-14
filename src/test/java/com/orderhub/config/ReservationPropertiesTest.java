package com.orderhub.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    void applicationYmlReservesStockForFifteenMinutes() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            ReservationProperties properties = context.getBean(ReservationProperties.class);
            assertThat(properties.getTtl()).isEqualTo(Duration.ofMinutes(15));
            assertThat(properties.getCleanupIntervalMs()).isEqualTo(60_000);
        });
    }

    @Test
    void misspelledReservationKeyFailsStartup() {
        contextRunner.withPropertyValues("app.reservation.time-to-live=5m")
                .run(context -> assertThat(context).hasFailed());
    }

    // deliberately not @Configuration: @SpringBootTest's component scan would pick it up and break the app context
    @EnableConfigurationProperties(ReservationProperties.class)
    static class PropertiesConfig {
    }
}
