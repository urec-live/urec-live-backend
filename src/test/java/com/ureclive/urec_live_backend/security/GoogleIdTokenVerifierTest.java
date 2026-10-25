package com.ureclive.urec_live_backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class GoogleIdTokenVerifierTest {
    @Test
    void acceptsVerifiedGoogleTokenForConfiguredAudience() {
        JwtDecoder decoder = ignored -> token(
                "https://accounts.google.com", List.of("urec-client"), "google-123", "Member@Example.com", true);

        GoogleIdTokenVerifier.GoogleProfile profile =
                new GoogleIdTokenVerifier("urec-client", decoder).verify("valid-token");

        assertEquals("google-123", profile.subject());
        assertEquals("member@example.com", profile.email());
    }

    @Test
    void rejectsWrongAudienceAndUnverifiedEmail() {
        JwtDecoder decoder = rawToken -> rawToken.equals("wrong-audience")
                ? token("https://accounts.google.com", List.of("other-client"), "google-123", "member@example.com", true)
                : token("https://accounts.google.com", List.of("urec-client"), "google-123", "member@example.com", false);
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier("urec-client", decoder);

        assertEquals(GoogleSignInException.Reason.REJECTED,
                assertThrows(GoogleSignInException.class, () -> verifier.verify("wrong-audience")).getReason());
        assertEquals(GoogleSignInException.Reason.REJECTED,
                assertThrows(GoogleSignInException.class, () -> verifier.verify("unverified-email")).getReason());
    }

    @Test
    void reportsMissingServerConfiguration() {
        JwtDecoder decoder = ignored -> { throw new AssertionError("Decoder must not be called"); };
        GoogleSignInException exception = assertThrows(GoogleSignInException.class,
                () -> new GoogleIdTokenVerifier("", decoder).verify("token"));

        assertEquals(GoogleSignInException.Reason.NOT_CONFIGURED, exception.getReason());
    }

    private Jwt token(String issuer, List<String> audience, String subject, String email, boolean emailVerified) {
        Instant issuedAt = Instant.now().minusSeconds(10);
        return new Jwt(
                "token",
                issuedAt,
                issuedAt.plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of(
                        "iss", issuer,
                        "aud", audience,
                        "sub", subject,
                        "email", email,
                        "email_verified", emailVerified));
    }
}
