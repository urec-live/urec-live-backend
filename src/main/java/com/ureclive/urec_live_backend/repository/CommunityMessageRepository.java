package com.ureclive.urec_live_backend.repository;

import com.ureclive.urec_live_backend.entity.CommunityMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommunityMessageRepository extends JpaRepository<CommunityMessage, Long> {

    /** One page of history, newest first: messages older than {@code beforeId} (Long.MAX_VALUE for the latest). */
    List<CommunityMessage> findByIdLessThanOrderByIdDesc(Long beforeId, Pageable page);

    Optional<CommunityMessage> findFirstBySenderUsernameAndClientId(String senderUsername, String clientId);
}
