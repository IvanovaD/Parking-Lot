package com.example.parkinglot.repository;

import com.example.parkinglot.AbstractPostgresIntegrationTest;
import com.example.parkinglot.domain.ParkingSpace;
import com.example.parkinglot.domain.Reservation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ReservationConstraintIT extends AbstractPostgresIntegrationTest {

    @Autowired
    ReservationRepository reservationRepository;
    @Autowired
    ParkingSpaceRepository parkingSpaceRepository;
    @Autowired
    IdempotencyRecordRepository idempotencyRecordRepository;

    ParkingSpace space;
    Instant start;

    @BeforeEach
    void setUp() {
        idempotencyRecordRepository.deleteAll();
        reservationRepository.deleteAll();
        space = parkingSpaceRepository.findBySpaceNumber(1).orElseThrow();
        start = Instant.parse("2030-01-01T10:00:00Z");
    }

    @Test
    void databaseRejectsOverlappingActiveReservationsForSameSpace() {
        reservationRepository.saveAndFlush(reservation(start, start.plusSeconds(3600)));

        assertThatThrownBy(() -> reservationRepository.saveAndFlush(
                reservation(start.plusSeconds(1800), start.plusSeconds(5400))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void adjacentReservationsDoNotOverlap() {
        reservationRepository.saveAndFlush(reservation(start, start.plusSeconds(3600)));

        assertThatCode(() -> reservationRepository.saveAndFlush(
                reservation(start.plusSeconds(3600), start.plusSeconds(7200))))
                .doesNotThrowAnyException();
    }

    private Reservation reservation(Instant startTime, Instant endTime) {
        return new Reservation(UUID.randomUUID(), space, "TEST", startTime, endTime, Instant.now());
    }
}
