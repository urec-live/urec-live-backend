package com.ureclive.urec_live_backend.repository;

import com.ureclive.urec_live_backend.entity.DirectMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DirectMessageRepository extends JpaRepository<DirectMessage, Long> {

    @Query("SELECT m FROM DirectMessage m WHERE (m.senderUsername = :a AND m.recipientUsername = :b) OR (m.senderUsername = :b AND m.recipientUsername = :a) ORDER BY m.sentAt ASC")
    List<DirectMessage> findConversation(@Param("a") String a, @Param("b") String b);

    @Query("SELECT m FROM DirectMessage m WHERE m.senderUsername = :me OR m.recipientUsername = :me ORDER BY m.sentAt DESC")
    List<DirectMessage> findAllInvolving(@Param("me") String me);

    long countByRecipientUsernameAndReadFalse(String recipientUsername);

    @Modifying
    @Query("UPDATE DirectMessage m SET m.read = true WHERE m.senderUsername = :sender AND m.recipientUsername = :recipient AND m.read = false")
    void markAllRead(@Param("sender") String sender, @Param("recipient") String recipient);
}
