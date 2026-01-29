package com.ureclive.urec_live_backend.repository;

import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.entity.UserWorkoutSplit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserWorkoutSplitRepository extends JpaRepository<UserWorkoutSplit, Long> {
    Optional<UserWorkoutSplit> findByUser(User user);

    void deleteByUser(User user);
}
