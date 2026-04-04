package com.ureclive.urec_live_backend.service;

import java.util.List;
import java.util.regex.Pattern;
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

    private static final Pattern EMAIL_PATTERN = 
        Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}$");
    private static final Pattern USERNAME_PATTERN = 
        Pattern.compile("^[a-zA-Z0-9_-]{3,20}$");

    private void validateCreateUserRequest(CreateUserRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }
        if (req.getUsername() == null || req.getUsername().isBlank()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        if (!USERNAME_PATTERN.matcher(req.getUsername()).matches()) {
            throw new IllegalArgumentException("Username must be 3-20 characters (alphanumeric, _, -)");
        }
        if (req.getEmail() == null || req.getEmail().isBlank()) {
            throw new IllegalArgumentException("Email cannot be empty");
        }
        if (!EMAIL_PATTERN.matcher(req.getEmail()).matches()) {
            throw new IllegalArgumentException("Invalid email format");
        }
        if (req.getPassword() == null || req.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password cannot be empty");
        }
        if (req.getPassword().length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
    }

    /** Return all users as response DTOs */
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    /** Admin creates a new user with specified roles */
    @Transactional
    public UserResponse createUser(CreateUserRequest req) {
        // Validate request
        validateCreateUserRequest(req);

        // Check username uniqueness
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new IllegalArgumentException("Username is already taken!");
        }
        
        // Check email uniqueness
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email is already registered!");
        }

        User user = new User(
            req.getUsername(),
            req.getEmail(),
            passwordEncoder.encode(req.getPassword())
        );

        // Assign requested roles, defaulting to USER if none provided
        List<String> roleNames = (req.getRoles() != null && !req.getRoles().isEmpty())
            ? req.getRoles()
            : List.of("ROLE_USER");

        for (String roleName : roleNames) {
            // Only allow predefined roles
            if (!roleName.startsWith("ROLE_")) {
                throw new IllegalArgumentException("Role must start with 'ROLE_': " + roleName);
            }
            Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName));
            user.addRole(role);
        }

        userRepository.save(user);
        activityLogService.log("ADMIN_CREATE_USER", req.getUsername(), "Admin created user", null);

        return toResponse(user);
    }

    /** Admin updates which roles a user has */
    @Transactional
    public UserResponse updateRoles(Long userId, UpdateRolesRequest req) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("Invalid user ID");
        }
        if (req == null || req.getRoles() == null || req.getRoles().isEmpty()) {
            throw new IllegalArgumentException("Roles list cannot be empty");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        // Validate all requested roles before modifying user
        for (String roleName : req.getRoles()) {
            if (!roleName.startsWith("ROLE_")) {
                throw new IllegalArgumentException("Role must start with 'ROLE_': " + roleName);
            }
            if (!roleRepository.findByName(roleName).isPresent()) {
                throw new IllegalArgumentException("Role not found: " + roleName);
            }
        }

        // Clear existing roles and assign new ones
        user.getRoles().clear();

        for (String roleName : req.getRoles()) {
            Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName));
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
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("Invalid user ID");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

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