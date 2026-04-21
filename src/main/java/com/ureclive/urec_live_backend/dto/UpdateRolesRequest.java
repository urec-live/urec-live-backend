package com.ureclive.urec_live_backend.dto;

import java.util.List;

public class UpdateRolesRequest {
    private List<String> roles;

    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }
}