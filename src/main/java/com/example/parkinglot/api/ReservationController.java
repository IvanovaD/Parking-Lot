package com.example.parkinglot.api;

import com.example.parkinglot.service.ReservationCommand;
import com.example.parkinglot.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @Operation(summary = "Reserve a parking space for one hour")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Reservation created or replayed"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "409", description = "Capacity, space, or idempotency conflict")
    })
    @PostMapping
    public ResponseEntity<ReservationResponse> reserve(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody CreateReservationRequest request) {
        var reservation = reservationService.reserve(
                idempotencyKey,
                new ReservationCommand(
                        request.licensePlate(),
                        request.startTime().toInstant(),
                        request.parkingSpaceNumber()
                )
        );
        URI location = URI.create("/api/v1/reservations/" + reservation.id());
        return ResponseEntity.created(location).body(ReservationResponse.from(reservation));
    }

    @Operation(summary = "Get a reservation")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reservation found"),
            @ApiResponse(responseCode = "404", description = "Reservation not found")
    })
    @GetMapping("/{reservationId}")
    public ReservationResponse get(@PathVariable UUID reservationId) {
        return ReservationResponse.from(reservationService.get(reservationId));
    }

    @Operation(summary = "Cancel a reservation")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Reservation cancelled or already cancelled"),
            @ApiResponse(responseCode = "404", description = "Reservation not found")
    })
    @DeleteMapping("/{reservationId}")
    public ResponseEntity<Void> cancel(@PathVariable UUID reservationId) {
        reservationService.cancel(reservationId);
        return ResponseEntity.noContent().build();
    }
}
