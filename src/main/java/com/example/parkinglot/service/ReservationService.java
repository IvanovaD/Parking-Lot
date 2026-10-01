package com.example.parkinglot.service;

import com.example.parkinglot.domain.IdempotencyRecord;
import com.example.parkinglot.domain.ParkingSpace;
import com.example.parkinglot.domain.Reservation;
import com.example.parkinglot.repository.IdempotencyRecordRepository;
import com.example.parkinglot.repository.ParkingSpaceRepository;
import com.example.parkinglot.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservationService {

    static final long ALLOCATION_LOCK_ID = 1L;
    static final int TOTAL_SPACES = 100;
    static final int MAX_ACTIVE_RESERVATIONS = 80;
    static final Duration RESERVATION_DURATION = Duration.ofHours(1);

    private final ParkingSpaceRepository parkingSpaceRepository;
    private final ReservationRepository reservationRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final Clock clock;

    @Transactional
    public ReservationView reserve(String idempotencyKey, ReservationCommand command) {
        Instant now = clock.instant();
        if (!command.startTime().isAfter(now)) {
            throw new ReservationException(HttpStatus.BAD_REQUEST, "START_TIME_NOT_FUTURE",
                    "startTime must be in the future");
        }

        String normalizedPlate = normalizePlate(command.licensePlate());
        Instant endTime = command.startTime().plus(RESERVATION_DURATION);
        String requestHash = hash(normalizedPlate, command.startTime(), command.requestedSpaceNumber());

        reservationRepository.acquireAllocationLock(ALLOCATION_LOCK_ID);

        var existingRecord = idempotencyRecordRepository.findWithReservationByKey(idempotencyKey);
        if (existingRecord.isPresent()) {
            if (!existingRecord.get().getRequestHash().equals(requestHash)) {
                throw new ReservationException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT",
                        "Idempotency-Key was already used with a different request");
            }
            return ReservationView.from(existingRecord.get().getReservation());
        }

        long overlappingReservations = reservationRepository
                .countActiveOverlapping(command.startTime(), endTime);
        if (overlappingReservations >= MAX_ACTIVE_RESERVATIONS) {
            throw new ReservationException(HttpStatus.CONFLICT, "MAX_UTILIZATION_REACHED",
                    "The parking lot has reached its maximum reservable utilization for this interval");
        }

        ParkingSpace parkingSpace = selectSpace(command.requestedSpaceNumber(),
                command.startTime(), endTime);

        Reservation reservation = new Reservation(UUID.randomUUID(), parkingSpace, normalizedPlate,
                command.startTime(), endTime, now);
        reservationRepository.save(reservation);
        idempotencyRecordRepository.save(new IdempotencyRecord(idempotencyKey, requestHash, reservation, now));

        return ReservationView.from(reservation);
    }

    @Transactional
    public void cancel(UUID reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(this::reservationNotFound);
        reservation.cancel(clock.instant());
    }

    @Transactional(readOnly = true)
    public ReservationView get(UUID reservationId) {
        return reservationRepository.findById(reservationId)
                .map(ReservationView::from)
                .orElseThrow(this::reservationNotFound);
    }

    private ReservationException reservationNotFound() {
        return new ReservationException(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND",
                "Reservation was not found");
    }

    private ParkingSpace selectSpace(Integer requestedSpaceNumber, Instant startTime, Instant endTime) {
        int selectedNumber;
        if (requestedSpaceNumber != null) {
            selectedNumber = requestedSpaceNumber;
        } else {
            selectedNumber = reservationRepository
                    .findLowestAvailableSpaceNumber(startTime, endTime)
                    .orElseThrow(() -> new ReservationException(HttpStatus.CONFLICT, "NO_SPACE_AVAILABLE",
                            "No parking space is available for this interval"));
        }

        ParkingSpace parkingSpace = parkingSpaceRepository
                .findBySpaceNumber(selectedNumber)
                .orElseThrow(() -> new ReservationException(HttpStatus.BAD_REQUEST, "INVALID_SPACE_NUMBER",
                        "parkingSpaceNumber must identify a space in this parking lot"));

        if (reservationRepository.existsActiveOverlap(parkingSpace.getId(), startTime, endTime)) {
            throw new ReservationException(HttpStatus.CONFLICT, "SPACE_UNAVAILABLE",
                    "The requested parking space is unavailable for this interval");
        }
        return parkingSpace;
    }

    static String normalizePlate(String plate) {
        return plate.trim().toUpperCase(Locale.ROOT);
    }

    static String hash(String plate, Instant startTime, Integer requestedSpaceNumber) {
        String canonical = plate + "\n" + startTime + "\n"
                + (requestedSpaceNumber == null ? "AUTO" : requestedSpaceNumber);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
