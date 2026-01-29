package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.dto.ChangePasswordRequest;
import com.ureclive.urec_live_backend.dto.UpdateEmailRequest;
import com.ureclive.urec_live_backend.dto.UserResponse;
import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.repository.UserRepository;
import com.ureclive.urec_live_backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        return ResponseEntity.ok(new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPushNotificationsEnabled()));
    }

    @PostMapping("/settings")
    public ResponseEntity<UserResponse> updateSettings(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody java.util.Map<String, Object> settings) {
        User user = getUser(userDetails);

        if (settings.containsKey("pushNotificationsEnabled")) {
            user.setPushNotificationsEnabled((Boolean) settings.get("pushNotificationsEnabled"));
            userRepository.save(user);
        }

        return ResponseEntity.ok(new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPushNotificationsEnabled()));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody ChangePasswordRequest request) {
        User user = getUser(userDetails);
        userService.changePassword(user, request.getOldPassword(), request.getNewPassword());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/update-email")
    public ResponseEntity<Void> updateEmail(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody UpdateEmailRequest request) {
        User user = getUser(userDetails);
        userService.updateEmail(user, request.getNewEmail());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMyAccount(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            User user = getUser(userDetails);
            userService.deleteUser(user);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            e.printStackTrace(); // Log the error to console
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/search")
    public java.util.List<UserResponse> searchUsers(@RequestParam("query") String query) {
        return userService.searchUsers(query).stream()
                .map(user -> new UserResponse(user.getId(), user.getUsername(), user.getEmail()))
                .collect(java.util.stream.Collectors.toList());
    }

    private User getUser(UserDetails userDetails) {
        return userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
