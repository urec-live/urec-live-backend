package com.ureclive.urec_live_backend.repository;

import com.ureclive.urec_live_backend.entity.PushToken;
import com.ureclive.urec_live_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PushTokenRepository extends JpaRepository<PushToken, Long> {
    Optional<PushToken> findByToken(String token);

    List<PushToken> findByUser(User user);

    void deleteByUser(User user);
}
