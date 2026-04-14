package com.ureclive.urec_live_backend.repository;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ureclive.urec_live_backend.entity.ActivityLog;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    Page<ActivityLog> findAllByOrderByTimestampDesc(Pageable pageable);

    // ✅ Uses native SQL instead of JPQL.
    // JPQL has a known issue where `:param IS NULL` doesn't work for String params
    // when the driver wraps them — native SQL handles it correctly with CAST.
    @Query(value = """
        SELECT * FROM activity_log a
        WHERE (:eventType IS NULL OR a.event_type = :eventType)
          AND (
                :search IS NULL
                OR LOWER(a.username) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(a.description) LIKE LOWER(CONCAT('%', :search, '%'))
                OR (a.equipment_name IS NOT NULL AND LOWER(a.equipment_name) LIKE LOWER(CONCAT('%', :search, '%')))
              )
          AND (:from IS NULL OR a.timestamp >= CAST(:from AS TIMESTAMP))
          AND (:to   IS NULL OR a.timestamp <= CAST(:to   AS TIMESTAMP))
        ORDER BY a.timestamp DESC
        """,
        countQuery = """
        SELECT COUNT(*) FROM activity_log a
        WHERE (:eventType IS NULL OR a.event_type = :eventType)
          AND (
                :search IS NULL
                OR LOWER(a.username) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(a.description) LIKE LOWER(CONCAT('%', :search, '%'))
                OR (a.equipment_name IS NOT NULL AND LOWER(a.equipment_name) LIKE LOWER(CONCAT('%', :search, '%')))
              )
          AND (:from IS NULL OR a.timestamp >= CAST(:from AS TIMESTAMP))
          AND (:to   IS NULL OR a.timestamp <= CAST(:to   AS TIMESTAMP))
        """,
        nativeQuery = true)
    Page<ActivityLog> findFiltered(
        @Param("eventType") String eventType,
        @Param("search")    String search,
        @Param("from")      String from,
        @Param("to")        String to,
        Pageable pageable
    );

    @Query("SELECT COUNT(a) FROM ActivityLog a WHERE a.eventType = :eventType AND a.timestamp >= :from")
    long countByEventTypeAndTimestampAfter(
        @Param("eventType") String eventType,
        @Param("from")      Instant from
    );
}