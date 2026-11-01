package com.ureclive.urec_live_backend.security;

public class GoogleSignInException extends RuntimeException {
    public enum Reason { NOT_CONFIGURED, REJECTED, ACCOUNT_CONFLICT }

    private final Reason reason;

    public GoogleSignInException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
