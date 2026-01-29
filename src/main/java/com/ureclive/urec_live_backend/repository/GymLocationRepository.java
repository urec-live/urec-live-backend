package com.ureclive.urec_live_backend.repository;

import com.ureclive.urec_live_backend.entity.GymLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GymLocationRepository extends JpaRepository<GymLocation, Long> {
    Optional<GymLocation> findByCode(String code);
}
