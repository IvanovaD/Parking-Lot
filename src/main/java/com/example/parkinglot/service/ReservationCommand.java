package com.example.parkinglot.service;

import java.time.Instant;

public record ReservationCommand(String licensePlate, Instant startTime, Integer requestedSpaceNumber) {
}
