package com.example.parkinglot.repository;

import com.example.parkinglot.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {

    @EntityGraph(attributePaths = {"reservation", "reservation.parkingSpace"})
    @Query("select record from IdempotencyRecord record where record.key = :key")
    Optional<IdempotencyRecord> findWithReservationByKey(@Param("key") String key);
}
