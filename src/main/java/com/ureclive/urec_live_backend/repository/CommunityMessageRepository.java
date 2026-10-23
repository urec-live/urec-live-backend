package com.ureclive.urec_live_backend.repository;

import com.ureclive.urec_live_backend.entity.CommunityMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommunityMessageRepository extends JpaRepository<CommunityMessage, Long> {
    List<CommunityMessage> findTop50ByOrderBySentAtAsc();
}
