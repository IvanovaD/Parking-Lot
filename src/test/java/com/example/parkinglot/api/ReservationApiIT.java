package com.example.parkinglot.api;

import com.example.parkinglot.AbstractPostgresIntegrationTest;
import com.example.parkinglot.repository.IdempotencyRecordRepository;
import com.example.parkinglot.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReservationApiIT extends AbstractPostgresIntegrationTest {

    @Autowired
    MockMvc mockMvc;
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
    void createsReplaysAndCancelsReservation() throws Exception {
        String start = Instant.now().plusSeconds(86_400).toString();
        String body = """
                {"licensePlate":" ca1234ab ","startTime":"%s","parkingSpaceNumber":42}
                """.formatted(start);

        String response = mockMvc.perform(post("/api/v1/reservations")
                        .header("Idempotency-Key", "api-test-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().exists("X-Correlation-ID"))
                .andExpect(jsonPath("$.licensePlate").value("CA1234AB"))
                .andExpect(jsonPath("$.parkingSpaceNumber").value(42))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();

        String id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("id").asText();

        mockMvc.perform(get("/api/v1/reservations/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.parkingSpaceNumber").value(42))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Idempotency-Key", "api-test-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id));

        mockMvc.perform(delete("/api/v1/reservations/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/reservations/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(delete("/api/v1/reservations/{id}", id))
                .andExpect(status().isNoContent());
    }

    @Test
    void returnsNotFoundForUnknownReservation() throws Exception {
        mockMvc.perform(get("/api/v1/reservations/{id}", java.util.UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESERVATION_NOT_FOUND"));
    }

    @Test
    void reportsValidationErrorsAsProblemDetails() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                        .header("Idempotency-Key", "api-test-bad")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"licensePlate":"","startTime":"2020-01-01T00:00:00Z","parkingSpaceNumber":101}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors", hasSize(2)));
    }
}
