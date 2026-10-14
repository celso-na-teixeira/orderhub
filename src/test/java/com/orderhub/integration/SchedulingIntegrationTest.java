package com.orderhub.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.config.ScheduledTaskHolder;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ScheduledTaskHolder scheduledTasks;

    @Test
    void reservationExpiryJobIsScheduled() {
        assertThat(scheduledTasks.getScheduledTasks())
                .anySatisfy(task -> assertThat(task.toString())
                        .contains("ReservationExpiryJob.expireStaleReservations"));
    }
}
