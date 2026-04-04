package com.ureclive.urec_live_backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ureclive.urec_live_backend.dto.CreateUserRequest;
import com.ureclive.urec_live_backend.dto.UpdateRolesRequest;
import com.ureclive.urec_live_backend.dto.UserResponse;
import com.ureclive.urec_live_backend.service.AdminUserService;

@RestController
@RequestMapping("/api/admin/users")
@CrossOrigin(origins = "*")
@PreAuthorize("hasAuthority('ADMIN') or hasAuthority('ROLE_ADMIN')")
public class AdminUserController {

    @Autowired
    private AdminUserService adminUserService;

    /** GET /api/admin/users — list all users */
    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(adminUserService.getAllUsers());
    }

    /** POST /api/admin/users — admin creates a new user */
    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody CreateUserRequest req) {
        try {
            UserResponse created = adminUserService.createUser(req);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
        }
    }

    /** PUT /api/admin/users/{id}/roles — update roles for a user */
    @PutMapping("/{id}/roles")
    public ResponseEntity<?> updateRoles(
            @PathVariable Long id,
            @RequestBody UpdateRolesRequest req) {
        try {
            UserResponse updated = adminUserService.updateRoles(id, req);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
        }
    }

    /** DELETE /api/admin/users/{id} — permanently delete a user */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        try {
            adminUserService.deleteUser(id);
            return ResponseEntity.noContent().build(); // 204
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
        }
    }
}