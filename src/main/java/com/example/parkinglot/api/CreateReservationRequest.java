package com.example.parkinglot.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record CreateReservationRequest(
        @NotBlank @Size(max = 20) String licensePlate,
        @NotNull OffsetDateTime startTime,
        @Min(1) @Max(100) Integer parkingSpaceNumber
) {
}
