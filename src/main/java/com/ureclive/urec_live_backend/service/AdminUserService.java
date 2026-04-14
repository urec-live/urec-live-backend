package com.ureclive.urec_live_backend.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ureclive.urec_live_backend.dto.CreateUserRequest;
import com.ureclive.urec_live_backend.dto.UpdateRolesRequest;
import com.ureclive.urec_live_backend.dto.UserResponse;
import com.ureclive.urec_live_backend.entity.Role;
import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.repository.RoleRepository;
import com.ureclive.urec_live_backend.repository.UserRepository;

@Service
public class AdminUserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ActivityLogService activityLogService;

    /** Return all users as response DTOs */
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    /** Admin creates a new user with specified roles */
    @Transactional
    public UserResponse createUser(CreateUserRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new RuntimeException("Username is already taken!");
        }
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new RuntimeException("Email is already registered!");
        }

        User user = new User(
            req.getUsername(),
            req.getEmail(),
            passwordEncoder.encode(req.getPassword())
        );

        // Assign requested roles, defaulting to USER if none provided
        List<String> roleNames = (req.getRoles() != null && !req.getRoles().isEmpty())
            ? req.getRoles()
            : List.of("USER");

        for (String roleName : roleNames) {
            Role role = roleRepository.findByName(roleName)
                .orElseGet(() -> roleRepository.save(new Role(roleName)));
            user.addRole(role);
        }

        userRepository.save(user);
        activityLogService.log("ADMIN_CREATE_USER", req.getUsername(), "Admin created user", null);

        return toResponse(user);
    }

    /** Admin updates which roles a user has */
    @Transactional
    public UserResponse updateRoles(Long userId, UpdateRolesRequest req) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        // Clear existing roles and assign new ones
        user.getRoles().clear();

        for (String roleName : req.getRoles()) {
            Role role = roleRepository.findByName(roleName)
                .orElseGet(() -> roleRepository.save(new Role(roleName)));
            user.addRole(role);
        }

        userRepository.save(user);
        activityLogService.log("ADMIN_UPDATE_ROLES", user.getUsername(),
            "Roles updated to: " + req.getRoles(), null);

        return toResponse(user);
    }

    /**
     * Permanently deletes the user from the database.
     * After deletion:
     *  - The user disappears from the admin panel (getAll returns them no longer)
     *  - The user cannot log in (CustomUserDetailsService throws UsernameNotFoundException)
     *  - The DB record is fully removed
     */
    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        String username = user.getUsername();
        userRepository.delete(user);
        activityLogService.log("ADMIN_DELETE_USER", username, "User permanently deleted", null);
    }

    private UserResponse toResponse(User user) {
        List<String> roles = user.getRoles().stream()
            .map(Role::getName)
            .collect(Collectors.toList());

        return new UserResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            roles,
            user.getEnabled(),
            user.getCreatedAt()   // make sure your User entity has getCreatedAt()
        );
    }
}