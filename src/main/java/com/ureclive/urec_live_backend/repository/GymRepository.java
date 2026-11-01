package com.ureclive.urec_live_backend.repository;

import com.ureclive.urec_live_backend.entity.Gym;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface GymRepository extends JpaRepository<Gym, Long> {
    Optional<Gym> findByName(String name);

    List<Gym> findByActiveTrueOrderByNameAsc();
}
