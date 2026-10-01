package com.example.parkinglot.repository;

import com.example.parkinglot.domain.ParkingSpace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ParkingSpaceRepository extends JpaRepository<ParkingSpace, Long> {
    Optional<ParkingSpace> findBySpaceNumber(int spaceNumber);
}
