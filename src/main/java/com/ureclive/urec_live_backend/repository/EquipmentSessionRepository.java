package com.ureclive.urec_live_backend.repository;

import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.EquipmentSession;
import com.ureclive.urec_live_backend.entity.EquipmentSessionStatus;
import com.ureclive.urec_live_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface EquipmentSessionRepository extends JpaRepository<EquipmentSession, Long> {

    Optional<EquipmentSession> findByEquipmentAndStatus(
            Equipment equipment,
            EquipmentSessionStatus status
    );

    Optional<EquipmentSession> findByUserAndEquipmentAndStatus(
            User user,
            Equipment equipment,
            EquipmentSessionStatus status
    );

    Optional<EquipmentSession> findTopByUserAndStatusOrderByStartedAtDesc(
            User user,
            EquipmentSessionStatus status
    );

    List<EquipmentSession> findByStatusAndStartedAtBefore(
            EquipmentSessionStatus status,
            Instant startedAt
    );

    @Query("""
            select s from EquipmentSession s
            where s.status = :status
              and (
                (s.lastHeartbeatAt is null and s.startedAt < :cutoff)
                or (s.lastHeartbeatAt is not null and s.lastHeartbeatAt < :cutoff)
              )
            """)
    List<EquipmentSession> findStaleSessions(
            @Param("status") EquipmentSessionStatus status,
            @Param("cutoff") Instant cutoff
    );

    @Query("""
            select s from EquipmentSession s
            where s.status = :status
              and coalesce(s.lastHeartbeatAt, s.startedAt) < :warnCutoff
              and (s.lastTimeoutWarningAt is null or s.lastTimeoutWarningAt < coalesce(s.lastHeartbeatAt, s.startedAt))
            """)
    List<EquipmentSession> findSessionsNeedingTimeoutWarning(
            @Param("status") EquipmentSessionStatus status,
            @Param("warnCutoff") Instant warnCutoff
    );

    List<EquipmentSession> findByUserAndStartedAtAfter(User user, Instant startedAt);

    List<EquipmentSession> findByStartedAtAfter(Instant startedAt);

    @Query("""
            select s from EquipmentSession s
            where s.startedAt < :windowEnd
              and (s.endedAt is null or s.endedAt > :windowStart)
            """)
    List<EquipmentSession> findSessionsOverlappingWindow(
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd
    );

    List<EquipmentSession> findByEquipmentAndStatusInAndEndedAtAfter(
            Equipment equipment,
            List<EquipmentSessionStatus> statuses,
            Instant endedAt
    );

    long countByStatusAndEndedAtIsNotNull(EquipmentSessionStatus status);

    long countByStatus(EquipmentSessionStatus status);

    long countByStatusInAndEndedAtIsNull(List<EquipmentSessionStatus> statuses);

    long countByStatusInAndEndReasonIsNull(List<EquipmentSessionStatus> statuses);

    @Query("""
            select s.id from EquipmentSession s
            left join EquipmentEvent e on e.session = s
            where s.startedAt >= :since
            group by s.id
            having count(e) = 0
            """)
    List<Long> findSessionIdsMissingEvents(@Param("since") Instant since);
}
