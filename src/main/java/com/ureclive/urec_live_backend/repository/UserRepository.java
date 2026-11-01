package com.ureclive.urec_live_backend.repository;

import com.ureclive.urec_live_backend.entity.User;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query(
            "select u from User u where u.username = :username")
    java.util.Optional<User> lockByUsername(
            @org.springframework.data.repository.query.Param("username") String username);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByGoogleSubject(String googleSubject);

    Boolean existsByUsername(String username);

    Boolean existsByEmail(String email);

    List<User> findByCreatedAtBetween(Instant from, Instant to);

    List<User> findByUsernameContainingIgnoreCaseAndProfileVisibility(
            String query, User.ProfileVisibility visibility, Pageable pageable);
}
