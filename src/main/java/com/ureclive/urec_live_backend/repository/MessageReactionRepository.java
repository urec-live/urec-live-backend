package com.ureclive.urec_live_backend.repository;

import com.ureclive.urec_live_backend.entity.ChatChannel;
import com.ureclive.urec_live_backend.entity.MessageReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageReactionRepository extends JpaRepository<MessageReaction, Long> {

    /** Every reaction on a page of messages in one query, oldest first so chips keep a stable order. */
    List<MessageReaction> findByChannelAndMessageIdInOrderByCreatedAtAscIdAsc(ChatChannel channel, Collection<Long> messageIds);

    Optional<MessageReaction> findByChannelAndMessageIdAndUsername(ChatChannel channel, Long messageId, String username);
}
