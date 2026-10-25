package com.ureclive.urec_live_backend.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

@Component
public class GoogleIdTokenVerifier {
    private static final String GOOGLE_CERTS_URL = "https://www.googleapis.com/oauth2/v3/certs";
    private static final List<String> VALID_ISSUERS = List.of("accounts.google.com", "https://accounts.google.com");

    private final String clientId;
    private final JwtDecoder jwtDecoder;

    @Autowired
    public GoogleIdTokenVerifier(@Value("${google.client-id:}") String clientId) {
        this(clientId, NimbusJwtDecoder.withJwkSetUri(GOOGLE_CERTS_URL).build());
    }

    GoogleIdTokenVerifier(String clientId, JwtDecoder jwtDecoder) {
        this.clientId = clientId == null ? "" : clientId.trim();
        this.jwtDecoder = jwtDecoder;
    }

    public GoogleProfile verify(String idToken) {
        if (clientId.isBlank()) {
            throw new GoogleSignInException(GoogleSignInException.Reason.NOT_CONFIGURED,
                    "Google sign-in is not configured.");
        }

        try {
            Jwt jwt = jwtDecoder.decode(idToken);
            String issuer = jwt.getIssuer() == null ? "" : jwt.getIssuer().toString();
            String subject = jwt.getSubject();
            String email = jwt.getClaimAsString("email");
            Boolean emailVerified = jwt.getClaimAsBoolean("email_verified");

            if (!VALID_ISSUERS.contains(issuer)
                    || !jwt.getAudience().contains(clientId)
                    || subject == null || subject.isBlank()
                    || email == null || email.isBlank() || email.indexOf('@') < 1
                    || !Boolean.TRUE.equals(emailVerified)) {
                throw rejected();
            }

            return new GoogleProfile(subject, email.trim().toLowerCase());
        } catch (JwtException | IllegalArgumentException exception) {
            throw rejected();
        }
    }

    private GoogleSignInException rejected() {
        return new GoogleSignInException(GoogleSignInException.Reason.REJECTED,
                "Google sign-in was rejected. Please try again.");
    }

    public record GoogleProfile(String subject, String email) {}
}
