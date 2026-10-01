package com.example.parkinglot.service;

import com.example.parkinglot.AbstractPostgresIntegrationTest;
import com.example.parkinglot.repository.IdempotencyRecordRepository;
import com.example.parkinglot.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ReservationConcurrencyIT extends AbstractPostgresIntegrationTest {

    @Autowired
    ReservationService reservationService;
    @Autowired
    ReservationRepository reservationRepository;
    @Autowired
    IdempotencyRecordRepository idempotencyRecordRepository;

    @BeforeEach
    void cleanReservations() {
        idempotencyRecordRepository.deleteAll();
        reservationRepository.deleteAll();
    }

    @Test
    void concurrentRequestsNeverExceedMaximumUtilization() {
        Instant start = Instant.now().plusSeconds(604_800);
        List<Boolean> outcomes = new ArrayList<>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < 81; i++) {
                int requestNumber = i;
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        reservationService.reserve("concurrent-" + requestNumber,
                                new ReservationCommand("CAR-" + requestNumber, start, null));
                        return true;
                    } catch (ReservationException exception) {
                        assertThat(exception.getErrorCode()).isEqualTo("MAX_UTILIZATION_REACHED");
                        return false;
                    }
                }, executor));
            }
            outcomes = futures.stream().map(CompletableFuture::join).toList();
        }

        assertThat(outcomes).containsExactlyInAnyOrderElementsOf(expectedOutcomes());
        assertThat(reservationRepository.countActiveOverlapping(start, start.plusSeconds(3600))).isEqualTo(80);
    }

    private List<Boolean> expectedOutcomes() {
        List<Boolean> expected = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            expected.add(true);
        }
        expected.add(false);
        return expected;
    }
}
