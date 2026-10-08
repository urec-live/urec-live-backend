package com.ureclive.urec_live_backend.repository;

import com.ureclive.urec_live_backend.entity.HelpRequest;
import com.ureclive.urec_live_backend.entity.HelpRequestStatus;
import com.ureclive.urec_live_backend.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface HelpRequestRepository extends JpaRepository<HelpRequest, Long> {

    @EntityGraph(attributePaths = {"member", "equipment", "exercise", "staffMember"})
    Optional<HelpRequest> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"member", "equipment", "exercise", "staffMember"})
    Optional<HelpRequest> findFirstByMemberAndStatusInOrderByCreatedAtDesc(User member,
                                                                           Collection<HelpRequestStatus> statuses);

    boolean existsByMemberAndStatusIn(User member, Collection<HelpRequestStatus> statuses);

    /** The staff queue: open requests, longest-waiting first. */
    @EntityGraph(attributePaths = {"member", "equipment", "exercise", "staffMember"})
    List<HelpRequest> findByStatusInOrderByCreatedAtAsc(Collection<HelpRequestStatus> statuses);

    @EntityGraph(attributePaths = {"member", "equipment", "exercise", "staffMember"})
    List<HelpRequest> findByStatusInOrderByClosedAtDesc(Collection<HelpRequestStatus> statuses, Pageable pageable);

    @EntityGraph(attributePaths = {"member", "equipment", "exercise", "staffMember"})
    List<HelpRequest> findByStatusInAndUpdatedAtBefore(Collection<HelpRequestStatus> statuses, Instant cutoff);
}
