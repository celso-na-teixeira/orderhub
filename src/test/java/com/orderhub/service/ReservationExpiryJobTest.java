package com.orderhub.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.Set;
import java.util.TreeSet;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationExpiryJobTest {

    @Mock
    private ReservationService reservationService;
    @Mock
    private OrderService orderService;

    @InjectMocks
    private ReservationExpiryJob job;

    @Test
    void oneFailingOrderDoesNotStopTheOthersFromExpiring() {
        when(reservationService.findOrdersWithStaleReservations()).thenReturn(new TreeSet<>(Set.of(1L, 2L)));
        doThrow(new ObjectOptimisticLockingFailureException("Order", 1L)).when(orderService).expireOrder(1L);

        job.expireStaleReservations();

        verify(orderService).expireOrder(2L);
    }
}
