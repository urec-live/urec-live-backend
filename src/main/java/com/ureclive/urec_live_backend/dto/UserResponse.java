// ── UserResponse.java ──────────────────────────────────────────────────────
// Place at: dto/UserResponse.java

package com.ureclive.urec_live_backend.dto;

import java.time.Instant;
import java.util.List;

public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private List<String> roles;
    private Boolean enabled;
    private Instant createdAt;

    public UserResponse() {}

    public UserResponse(Long id, String username, String email,
                        List<String> roles, Boolean enabled, Instant createdAt) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.roles = roles;
        this.enabled = enabled;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}


// ── CreateUserRequest.java ─────────────────────────────────────────────────
// Place at: dto/CreateUserRequest.java

// package com.ureclive.urec_live_backend.dto;
//
// import java.util.List;
//
// public class CreateUserRequest {
//     private String username;
//     private String email;
//     private String password;
//     private List<String> roles;
//
//     // getters & setters
//     public String getUsername() { return username; }
//     public void setUsername(String username) { this.username = username; }
//     public String getEmail() { return email; }
//     public void setEmail(String email) { this.email = email; }
//     public String getPassword() { return password; }
//     public void setPassword(String password) { this.password = password; }
//     public List<String> getRoles() { return roles; }
//     public void setRoles(List<String> roles) { this.roles = roles; }
// }
//
// NOTE: You can reuse your existing RegisterRequest DTO for creation —
// just add a `roles` field to it, OR create a new CreateUserRequest class above.


// ── UpdateRolesRequest.java ────────────────────────────────────────────────
// Place at: dto/UpdateRolesRequest.java

// package com.ureclive.urec_live_backend.dto;
//
// import java.util.List;
//
// public class UpdateRolesRequest {
//     private List<String> roles;
//     public List<String> getRoles() { return roles; }
//     public void setRoles(List<String> roles) { this.roles = roles; }
// }