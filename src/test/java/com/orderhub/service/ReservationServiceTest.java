package com.orderhub.service;

import com.orderhub.config.ReservationProperties;
import com.orderhub.domain.Reservation;
import com.orderhub.domain.ReservationStatus;
import com.orderhub.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        ReservationProperties properties = new ReservationProperties();
        properties.setTtl(Duration.ofMinutes(15));
        reservationService = new ReservationService(reservationRepository, properties);
    }

    @Test
    void createdReservationExpiresAfterTtl() {
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Reservation reservation = reservationService.create(1L, 12L, 2);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(reservation.getExpiresAt())
                .isCloseTo(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(15), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void ordersWithStaleReservationsAreFoundOncePerOrder() {
        LocalDateTime past = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5);
        when(reservationRepository.findByStatusAndExpiresAtBefore(eq(ReservationStatus.ACTIVE), any()))
                .thenReturn(List.of(new Reservation(1L, 12L, 2, past), new Reservation(1L, 13L, 1, past),
                        new Reservation(2L, 12L, 1, past)));

        assertThat(reservationService.findOrdersWithStaleReservations()).containsExactly(1L, 2L);
    }

    @Test
    void staleReservationsAreLookedUpAgainstUtcTime() {
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        when(reservationRepository.findByStatusAndExpiresAtBefore(eq(ReservationStatus.ACTIVE), cutoff.capture()))
                .thenReturn(List.of());

        reservationService.findOrdersWithStaleReservations();

        assertThat(cutoff.getValue()).isCloseTo(LocalDateTime.now(ZoneOffset.UTC), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void expiringForOrderMarksActiveReservationsExpired() {
        Reservation active = new Reservation(1L, 12L, 2, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5));
        when(reservationRepository.findByOrderIdAndStatus(1L, ReservationStatus.ACTIVE)).thenReturn(List.of(active));

        assertThat(reservationService.expireForOrder(1L)).containsExactly(active);
        assertThat(active.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
    }

    @Test
    void orderWithActiveReservationPastItsTtlCountsAsExpired() {
        when(reservationRepository.findByOrderId(1L)).thenReturn(List.of(
                new Reservation(1L, 12L, 2, LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1))));

        assertThat(reservationService.hasExpiredReservation(1L)).isTrue();
    }

    @Test
    void orderWithLiveReservationIsNotExpired() {
        when(reservationRepository.findByOrderId(1L)).thenReturn(List.of(
                new Reservation(1L, 12L, 2, LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10))));

        assertThat(reservationService.hasExpiredReservation(1L)).isFalse();
    }
}
