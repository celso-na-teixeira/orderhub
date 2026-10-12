package com.orderhub.service;

import com.orderhub.config.ReservationProperties;
import com.orderhub.domain.Reservation;
import com.orderhub.domain.ReservationStatus;
import com.orderhub.repository.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@Transactional
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final ReservationRepository reservationRepository;
    private final ReservationProperties properties;

    public ReservationService(ReservationRepository reservationRepository, ReservationProperties properties) {
        this.reservationRepository = reservationRepository;
        this.properties = properties;
    }

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
     * Expires reservations of unpaid orders once their time-to-live has passed.
     */
    @Scheduled(fixedDelayString = "${app.reservation.cleanup-interval-ms:60000}")
    public void expireStaleReservations() {
        List<Reservation> stale = reservationRepository
                .findByStatusAndExpiresAtBefore(ReservationStatus.ACTIVE, LocalDateTime.now());
        stale.forEach(r -> r.setStatus(ReservationStatus.EXPIRED));
        if (!stale.isEmpty()) {
            log.info("Expired {} stale reservations", stale.size());
        }
    }
}
