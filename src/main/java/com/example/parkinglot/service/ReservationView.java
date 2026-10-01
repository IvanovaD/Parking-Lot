package com.example.parkinglot.service;

import com.example.parkinglot.domain.Reservation;
import com.example.parkinglot.domain.ReservationStatus;

import java.time.Instant;
import java.util.UUID;

public record ReservationView(
        UUID id,
        String licensePlate,
        int parkingSpaceNumber,
        Instant startTime,
        Instant endTime,
        ReservationStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static ReservationView from(Reservation reservation) {
        return new ReservationView(
                reservation.getId(),
                reservation.getLicensePlate(),
                reservation.getParkingSpace().getSpaceNumber(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getStatus(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt()
        );
    }
}
