package com.orderhub.service;

import com.orderhub.config.ReservationProperties;
import com.orderhub.domain.Reservation;
import com.orderhub.domain.ReservationStatus;
import com.orderhub.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ReservationProperties properties;

    public Reservation create(Long orderId, Long productId, int quantity) {
        LocalDateTime expiresAt = LocalDateTime.now(ZoneOffset.UTC).plus(properties.getTtl());
        Reservation reservation = reservationRepository.save(new Reservation(orderId, productId, quantity, expiresAt));
        log.debug("Reservation {} created for order {} product {} (expires {})",
                reservation.getId(), orderId, productId, expiresAt);
        return reservation;
    }

    @Transactional(readOnly = true)
    public List<Reservation> findActiveForOrder(Long orderId) {
        return reservationRepository.findByOrderIdAndStatus(orderId, ReservationStatus.ACTIVE);
    }

    public void markConverted(Long orderId) {
        reservationRepository.findByOrderIdAndStatus(orderId, ReservationStatus.ACTIVE)
                .forEach(r -> r.setStatus(ReservationStatus.CONVERTED));
    }

    public void releaseForOrder(Long orderId) {
        reservationRepository.findByOrderId(orderId)
                .forEach(r -> r.setStatus(ReservationStatus.RELEASED));
    }

    /**
     * Orders holding at least one active reservation whose time-to-live has passed (BR-30, BR-33).
     */
    @Transactional(readOnly = true)
    public Set<Long> findOrdersWithStaleReservations() {
        return reservationRepository
                .findByStatusAndExpiresAtBefore(ReservationStatus.ACTIVE, LocalDateTime.now(ZoneOffset.UTC))
                .stream()
                .map(Reservation::getOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * Whether the order still holds a reservation that has run out, so it can no longer be paid (BR-32).
     */
    @Transactional(readOnly = true)
    public boolean hasExpiredReservation(Long orderId) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return reservationRepository.findByOrderId(orderId).stream()
                .anyMatch(r -> r.getStatus() == ReservationStatus.EXPIRED
                        || (r.getStatus() == ReservationStatus.ACTIVE && r.isExpired(now)));
    }

    /**
     * Marks the order's active reservations as expired and returns them, so the caller can release their stock.
     */
    public List<Reservation> expireForOrder(Long orderId) {
        List<Reservation> active = reservationRepository.findByOrderIdAndStatus(orderId, ReservationStatus.ACTIVE);
        active.forEach(r -> r.setStatus(ReservationStatus.EXPIRED));
        return active;
    }
}
