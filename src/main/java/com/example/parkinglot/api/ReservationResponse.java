package com.example.parkinglot.api;

import com.example.parkinglot.domain.ReservationStatus;
import com.example.parkinglot.service.ReservationView;

import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        String licensePlate,
        int parkingSpaceNumber,
        Instant startTime,
        Instant endTime,
        ReservationStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static ReservationResponse from(ReservationView reservation) {
        return new ReservationResponse(
                reservation.id(),
                reservation.licensePlate(),
                reservation.parkingSpaceNumber(),
                reservation.startTime(),
                reservation.endTime(),
                reservation.status(),
                reservation.createdAt(),
                reservation.updatedAt()
        );
    }
}
