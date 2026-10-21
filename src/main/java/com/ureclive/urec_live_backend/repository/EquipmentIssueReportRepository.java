package com.ureclive.urec_live_backend.repository;

import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.EquipmentIssueReport;
import com.ureclive.urec_live_backend.entity.IssueStatus;
import com.ureclive.urec_live_backend.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EquipmentIssueReportRepository extends JpaRepository<EquipmentIssueReport, Long> {

    /** Reports not in {@code status} on machines that haven't been removed, newest first. */
    @EntityGraph(attributePaths = {"equipment", "reporter"})
    List<EquipmentIssueReport> findByStatusNotAndEquipmentDeletedFalseOrderByReportedAtDesc(IssueStatus status);

    @EntityGraph(attributePaths = {"equipment", "reporter"})
    List<EquipmentIssueReport> findByEquipmentDeletedFalseOrderByReportedAtDesc();

    @EntityGraph(attributePaths = {"equipment", "reporter"})
    List<EquipmentIssueReport> findByReporterOrderByReportedAtDesc(User reporter);

    @EntityGraph(attributePaths = {"equipment", "reporter"})
    List<EquipmentIssueReport> findByEquipmentIdAndStatusNotOrderByReportedAtDesc(Long equipmentId, IssueStatus status);

    boolean existsByReporterAndEquipmentAndStatusNot(User reporter, Equipment equipment, IssueStatus status);

    boolean existsByEquipmentIdAndStatusNot(Long equipmentId, IssueStatus status);

    long countByStatusAndEquipmentDeletedFalse(IssueStatus status);

    /** Number of machines (not removed) with at least one report not in {@code status}. */
    @Query("SELECT COUNT(DISTINCT r.equipment.id) FROM EquipmentIssueReport r " +
           "WHERE r.status <> :status AND r.equipment.deleted = false")
    long countDistinctEquipmentByStatusNot(@Param("status") IssueStatus status);
}
