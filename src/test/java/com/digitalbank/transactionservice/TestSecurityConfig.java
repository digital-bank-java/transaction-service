package com.digitalbank.transactionservice;

import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@TestConfiguration
public class TestSecurityConfig {

    public static final String AUTHORIZED_AUTHORIZATION = "Bearer test-transfer-jwt";
    public static final String UNAUTHORIZED_SUBJECT_AUTHORIZATION = "Bearer test-unapproved-subject-jwt";
    public static final String INSUFFICIENT_SCOPE_AUTHORIZATION = "Bearer test-insufficient-scope-jwt";

    @Bean
    JwtDecoder testJwtDecoder() {
        return token -> {
            var now = Instant.now();
            return switch (token) {
                case "test-transfer-jwt" -> jwt(token, "transfer-orchestrator", "transfer.internal", now);
                case "test-unapproved-subject-jwt" -> jwt(token, "unapproved-client", "transfer.internal", now);
                case "test-insufficient-scope-jwt" -> jwt(token, "transfer-orchestrator", "other.internal", now);
                default -> throw new IllegalArgumentException("Invalid test bearer token");
            };
        };
    }

    private static Jwt jwt(String token, String subject, String scope, Instant now) {
        return Jwt.withTokenValue(token)
                .header("alg", "none")
                .subject(subject)
                .issuer("https://issuer.test.internal")
                .issuedAt(now.minusSeconds(60))
                .expiresAt(now.plusSeconds(3600))
                .claim("scope", scope)
                .build();
    }
}
