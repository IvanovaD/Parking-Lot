package com.example.parkinglot.service;

import com.example.parkinglot.domain.IdempotencyRecord;
import com.example.parkinglot.domain.ParkingSpace;
import com.example.parkinglot.domain.Reservation;
import com.example.parkinglot.domain.ReservationStatus;
import com.example.parkinglot.repository.IdempotencyRecordRepository;
import com.example.parkinglot.repository.ParkingSpaceRepository;
import com.example.parkinglot.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");
    private static final Instant START = Instant.parse("2026-10-02T10:15:00Z");

    @Mock
    ParkingSpaceRepository parkingSpaceRepository;
    @Mock
    ReservationRepository reservationRepository;
    @Mock
    IdempotencyRecordRepository idempotencyRecordRepository;

    ReservationService service;
    @BeforeEach
    void setUp() {
        service = new ReservationService(parkingSpaceRepository, reservationRepository,
                idempotencyRecordRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void automaticallyAssignsLowestAvailableSpaceForExactlyOneHour() {
        ParkingSpace space = space(7L, 3);
        when(idempotencyRecordRepository.findWithReservationByKey("request-1")).thenReturn(Optional.empty());
        when(reservationRepository.countActiveOverlapping(START, START.plusSeconds(3600))).thenReturn(4L);
        when(reservationRepository.findLowestAvailableSpaceNumber(START, START.plusSeconds(3600)))
                .thenReturn(Optional.of(3));
        when(parkingSpaceRepository.findBySpaceNumber(3)).thenReturn(Optional.of(space));

        ReservationView result = service.reserve("request-1",
                new ReservationCommand(" ca1234ab ", START, null));

        assertThat(result.licensePlate()).isEqualTo("CA1234AB");
        assertThat(result.parkingSpaceNumber()).isEqualTo(3);
        assertThat(result.endTime()).isEqualTo(START.plusSeconds(3600));
        assertThat(result.status()).isEqualTo(ReservationStatus.ACTIVE);
        verify(reservationRepository).acquireAllocationLock(ReservationService.ALLOCATION_LOCK_ID);
        verify(idempotencyRecordRepository).save(org.mockito.ArgumentMatchers.any(IdempotencyRecord.class));
    }

    @Test
    void rejectsRequestedSpaceWhenItOverlaps() {
        ParkingSpace space = space(42L, 42);
        when(idempotencyRecordRepository.findWithReservationByKey("request-2")).thenReturn(Optional.empty());
        when(parkingSpaceRepository.findBySpaceNumber(42)).thenReturn(Optional.of(space));
        when(reservationRepository.existsActiveOverlap(42L, START, START.plusSeconds(3600))).thenReturn(true);

        assertThatThrownBy(() -> service.reserve("request-2",
                new ReservationCommand("CA1234AB", START, 42)))
                .isInstanceOf(ReservationException.class)
                .extracting("errorCode")
                .isEqualTo("SPACE_UNAVAILABLE");
        verify(reservationRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsReservationAtMaximumUtilization() {
        when(idempotencyRecordRepository.findWithReservationByKey("request-3")).thenReturn(Optional.empty());
        when(reservationRepository.countActiveOverlapping(START, START.plusSeconds(3600))).thenReturn(80L);

        assertThatThrownBy(() -> service.reserve("request-3",
                new ReservationCommand("CA1234AB", START, null)))
                .isInstanceOf(ReservationException.class)
                .extracting("errorCode")
                .isEqualTo("MAX_UTILIZATION_REACHED");
    }

    @Test
    void rejectsStartThatIsNotFutureBeforeLocking() {
        assertThatThrownBy(() -> service.reserve("request-4",
                new ReservationCommand("CA1234AB", NOW, null)))
                .isInstanceOf(ReservationException.class)
                .extracting("errorCode")
                .isEqualTo("START_TIME_NOT_FUTURE");
        verify(reservationRepository, never()).acquireAllocationLock(ReservationService.ALLOCATION_LOCK_ID);
    }

    @Test
    void returnsExistingReservationForMatchingIdempotentReplay() {
        ParkingSpace space = space(9L, 9);
        Reservation reservation = new Reservation(UUID.randomUUID(), space, "CA1234AB", START,
                START.plusSeconds(3600), NOW);
        IdempotencyRecord record = new IdempotencyRecord("same-key",
                ReservationService.hash("CA1234AB", START, 9), reservation, NOW);
        when(idempotencyRecordRepository.findWithReservationByKey("same-key")).thenReturn(Optional.of(record));

        ReservationView result = service.reserve("same-key",
                new ReservationCommand("ca1234ab", START, 9));

        assertThat(result.id()).isEqualTo(reservation.getId());
        verify(reservationRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsIdempotencyKeyReusedWithDifferentPayload() {
        ParkingSpace space = space(9L, 9);
        Reservation reservation = new Reservation(UUID.randomUUID(), space, "CA1234AB", START,
                START.plusSeconds(3600), NOW);
        IdempotencyRecord record = new IdempotencyRecord("same-key",
                ReservationService.hash("CA1234AB", START, 9), reservation, NOW);
        when(idempotencyRecordRepository.findWithReservationByKey("same-key")).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.reserve("same-key",
                new ReservationCommand("DIFFERENT", START, 9)))
                .isInstanceOf(ReservationException.class)
                .extracting("errorCode")
                .isEqualTo("IDEMPOTENCY_KEY_CONFLICT");
        verify(reservationRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void cancellationIsIdempotent() {
        Reservation reservation = mock(Reservation.class);
        UUID id = UUID.randomUUID();
        when(reservationRepository.findById(id)).thenReturn(Optional.of(reservation));

        service.cancel(id);

        verify(reservation).cancel(NOW);
    }

    @Test
    void getsReservationById() {
        ParkingSpace space = space(12L, 12);
        UUID id = UUID.randomUUID();
        Reservation reservation = new Reservation(id, space, "CA1234AB", START,
                START.plusSeconds(3600), NOW);
        when(reservationRepository.findById(id)).thenReturn(Optional.of(reservation));

        ReservationView result = service.get(id);

        assertThat(result.id()).isEqualTo(id);
        assertThat(result.parkingSpaceNumber()).isEqualTo(12);
    }

    @Test
    void reportsMissingReservationOnGet() {
        UUID id = UUID.randomUUID();
        when(reservationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
                .isInstanceOf(ReservationException.class)
                .extracting("errorCode")
                .isEqualTo("RESERVATION_NOT_FOUND");
    }

    private ParkingSpace space(long id, int number) {
        ParkingSpace space = mock(ParkingSpace.class);
        lenient().when(space.getId()).thenReturn(id);
        lenient().when(space.getSpaceNumber()).thenReturn(number);
        return space;
    }

}
