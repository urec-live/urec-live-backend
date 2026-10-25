package com.ureclive.urec_live_backend.service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ureclive.urec_live_backend.dto.AuthResponse;
import com.ureclive.urec_live_backend.entity.Role;
import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.repository.RoleRepository;
import com.ureclive.urec_live_backend.repository.UserRepository;
import com.ureclive.urec_live_backend.security.GoogleIdTokenVerifier;
import com.ureclive.urec_live_backend.security.GoogleIdTokenVerifier.GoogleProfile;
import com.ureclive.urec_live_backend.security.GoogleSignInException;
import com.ureclive.urec_live_backend.security.JwtUtil;

@Service
public class GoogleAuthService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final ActivityLogService activityLogService;
    private final GoogleIdTokenVerifier tokenVerifier;

    public GoogleAuthService(UserRepository userRepository,
                             RoleRepository roleRepository,
                             PasswordEncoder passwordEncoder,
                             JwtUtil jwtUtil,
                             ActivityLogService activityLogService,
                             GoogleIdTokenVerifier tokenVerifier) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.activityLogService = activityLogService;
        this.tokenVerifier = tokenVerifier;
    }

    @Transactional
    public AuthResponse login(String idToken) {
        GoogleProfile profile = tokenVerifier.verify(idToken);
        User user = userRepository.findByGoogleSubject(profile.subject())
                .orElseGet(() -> linkOrCreateUser(profile));

        if (!user.getEmail().equalsIgnoreCase(profile.email())) {
            throw new GoogleSignInException(GoogleSignInException.Reason.ACCOUNT_CONFLICT,
                    "This Google account does not match the linked UREC Live account.");
        }

        List<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toList());
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities(roles.toArray(String[]::new))
                .build();

        return new AuthResponse(
                jwtUtil.generateToken(userDetails),
                jwtUtil.generateRefreshToken(userDetails),
                user.getUsername(),
                user.getEmail(),
                roles);
    }

    private User linkOrCreateUser(GoogleProfile profile) {
        return userRepository.findByEmailIgnoreCase(profile.email())
                .map(existing -> linkExistingUser(existing, profile.subject()))
                .orElseGet(() -> createUser(profile));
    }

    private User linkExistingUser(User user, String googleSubject) {
        if (user.getGoogleSubject() != null && !user.getGoogleSubject().equals(googleSubject)) {
            throw new GoogleSignInException(GoogleSignInException.Reason.ACCOUNT_CONFLICT,
                    "This email is already linked to another Google account.");
        }
        user.setGoogleSubject(googleSubject);
        return userRepository.save(user);
    }

    private User createUser(GoogleProfile profile) {
        Role userRole = roleRepository.findByName("USER")
                .orElseGet(() -> roleRepository.save(new Role("USER")));
        User user = new User(
                uniqueUsername(profile.email()),
                profile.email(),
                passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setGoogleSubject(profile.subject());
        user.addRole(userRole);
        User saved = userRepository.save(user);
        activityLogService.log("GOOGLE_REGISTRATION", saved.getUsername(), "New user registered with Google", null);
        return saved;
    }

    private String uniqueUsername(String email) {
        String localPart = email.substring(0, email.indexOf('@'));
        String base = localPart.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "");
        if (base.isBlank()) base = "member";
        String candidate = base;
        int suffix = 2;
        while (userRepository.existsByUsername(candidate)) {
            candidate = base + suffix++;
        }
        return candidate;
    }
}
