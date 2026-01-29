package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.repository.EquipmentEventRepository;
import com.ureclive.urec_live_backend.repository.EquipmentSessionRepository;
import com.ureclive.urec_live_backend.repository.PushTokenRepository;
import com.ureclive.urec_live_backend.repository.UserRepository;
import com.ureclive.urec_live_backend.repository.UserWorkoutSplitRepository;
import com.ureclive.urec_live_backend.repository.WorkoutSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Transactional
    public void changePassword(User user, String oldPassword, String newPassword) {
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new RuntimeException("Invalid old password");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new RuntimeException("New password cannot be empty");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public void updateEmail(User user, String newEmail) {
        if (newEmail == null || newEmail.isBlank()) {
            throw new RuntimeException("Email cannot be empty");
        }
        if (newEmail.equals(user.getEmail())) {
            return; // No change
        }
        if (userRepository.existsByEmail(newEmail)) {
            throw new RuntimeException("Email is already taken");
        }
        user.setEmail(newEmail);
        userRepository.save(user);
    }

    @Autowired
    private EquipmentSessionRepository equipmentSessionRepository;

    @Autowired
    private UserWorkoutSplitRepository userWorkoutSplitRepository;

    @Autowired
    private PushTokenRepository pushTokenRepository;

    @Autowired
    private WorkoutSessionRepository workoutSessionRepository;

    @Autowired
    private EquipmentEventRepository equipmentEventRepository;

    @Transactional
    @SuppressWarnings("null")
    public void deleteUser(User user) {
        // Hard delete: remove all dependent data first (cascade)
        equipmentEventRepository.deleteByUser(user); // Dependent on EquipmentSession, so delete first
        equipmentSessionRepository.deleteByUser(user);
        userWorkoutSplitRepository.deleteByUser(user);
        pushTokenRepository.deleteByUser(user);
        workoutSessionRepository.deleteByUser(user);

        // Finally delete the user
        userRepository.delete(user);
    }

    @Transactional(readOnly = true)
    public java.util.List<User> searchUsers(String query) {
        if (query == null || query.isBlank()) {
            return java.util.Collections.emptyList();
        }
        return userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(query, query);
    }
}
