package com.ureclive.urec_live_backend.repository;

import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.entity.UserLeagueScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserLeagueScoreRepository extends JpaRepository<UserLeagueScore, Long> {
    Optional<UserLeagueScore> findByUserAndWeekStart(User user, LocalDate weekStart);
    List<UserLeagueScore> findByWeekStartAndTierOrderByRankInTierAsc(LocalDate weekStart, String tier);
}
