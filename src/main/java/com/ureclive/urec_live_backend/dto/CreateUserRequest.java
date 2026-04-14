 package com.ureclive.urec_live_backend.dto;

import java.util.List;

public class CreateUserRequest {
    private String username;
    private String email;
    private String password;
    private List<String> roles;

    // getters & setters
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    
    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }
}

// NOTE: You can reuse your existing RegisterRequest DTO for creation —
// just add a `roles` field to it, OR create a new CreateUserRequest class above.


