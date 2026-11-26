package com.ureclive.urec_live_backend.repository;

import com.ureclive.urec_live_backend.entity.ChatMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ChatMediaRepository extends JpaRepository<ChatMedia, Long> {

    /**
     * Marks the uploader's photo as used by a message, in one conditional update, so two sends racing
     * for the same photo can't both get it. Returns 1 when this call claimed it, 0 otherwise.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ChatMedia m set m.attached = true "
            + "where m.id = :id and m.uploaderUsername = :uploader and m.attached = false")
    int markAttached(@Param("id") Long id, @Param("uploader") String uploader);

    /** Uploads not yet sent on a message, for the per-member cap. */
    long countByUploaderUsernameAndAttachedFalseAndCreatedAtAfter(String uploaderUsername, Instant since);
}
