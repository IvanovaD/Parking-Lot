package com.example.parkinglot.repository;

import com.example.parkinglot.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    @Query(value = "SELECT pg_advisory_xact_lock(:lockId)", nativeQuery = true)
    void acquireAllocationLock(@Param("lockId") long lockId);

    @Query("""
            select count(r) from Reservation r
            where r.status = ReservationStatus.ACTIVE
              and r.startTime < :endTime
              and r.endTime > :startTime
            """)
    long countActiveOverlapping(@Param("startTime") Instant startTime, @Param("endTime") Instant endTime);

    @Query("""
            select (count(r) > 0) from Reservation r
            where r.parkingSpace.id = :spaceId
              and r.status = ReservationStatus.ACTIVE
              and r.startTime < :endTime
              and r.endTime > :startTime
            """)
    boolean existsActiveOverlap(@Param("spaceId") Long spaceId,
                                @Param("startTime") Instant startTime,
                                @Param("endTime") Instant endTime);

    @Query(value = """
            SELECT ps.space_number
            FROM parking_space ps
            WHERE NOT EXISTS (
                  SELECT 1 FROM reservation r
                  WHERE r.parking_space_id = ps.id
                    AND r.status = 'ACTIVE'
                    AND r.start_time < :endTime
                    AND r.end_time > :startTime
              )
            ORDER BY ps.space_number
            LIMIT 1
            """, nativeQuery = true)
    Optional<Integer> findLowestAvailableSpaceNumber(@Param("startTime") Instant startTime,
                                                      @Param("endTime") Instant endTime);
}
