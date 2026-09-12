package com.digitalbank.transactionservice.security;

import java.util.Base64;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
class JwtDecoderConfiguration {

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    JwtDecoder jwtDecoder(Environment environment) {
        String issuerUri = environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri");
        String jwkSetUri = environment.getProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri");

        if (issuerUri != null && !issuerUri.isBlank()) {
            if (jwkSetUri != null && !jwkSetUri.isBlank()) {
                var decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
                return configure(decoder, issuerUri, environment);
            }

            return configure((NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(issuerUri), issuerUri, environment);
        }

        String secret = requiredProperty(environment, "auth.jwt.secret");
        String issuer = requiredProperty(environment, "auth.jwt.issuer");
        String audience = requiredProperty(environment, "auth.jwt.audience");
        String tokenPurpose = requiredProperty(environment, "auth.jwt.token-purpose");
        byte[] decodedSecret = decodeSecret(secret);
        MacAlgorithm macAlgorithm = macAlgorithmFor(decodedSecret.length);
        var decoder = NimbusJwtDecoder.withSecretKey(
                        new SecretKeySpec(decodedSecret, jcaAlgorithmFor(macAlgorithm)))
                .macAlgorithm(macAlgorithm)
                .build();
        return configure(decoder, issuer, audience, tokenPurpose);
    }

    @Bean
    @ConditionalOnProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri")
    Object requireIssuerForExplicitJwkSet(Environment environment) {
        requiredProperty(environment, "spring.security.oauth2.resourceserver.jwt.issuer-uri");
        return new Object();
    }

    private static String requiredProperty(Environment environment, String propertyName) {
        String value = environment.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " must be configured");
        }
        return value;
    }

    private static JwtDecoder configure(NimbusJwtDecoder decoder, String issuer, Environment environment) {
        return configure(
                decoder,
                issuer,
                requiredProperty(environment, "auth.jwt.audience"),
                requiredProperty(environment, "auth.jwt.token-purpose"));
    }

    private static JwtDecoder configure(NimbusJwtDecoder decoder, String issuer, String audience, String tokenPurpose) {
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                "aud", values -> values != null && values.contains(audience));
        OAuth2TokenValidator<Jwt> purposeValidator = new JwtClaimValidator<String>(
                "token_purpose", tokenPurpose::equals);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator, purposeValidator));
        return decoder;
    }

    private static byte[] decodeSecret(String encodedSecret) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encodedSecret);
            if (decoded.length < 32) {
                throw new IllegalStateException("auth.jwt.secret must decode to at least 32 bytes");
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("auth.jwt.secret must be valid base64", exception);
        }
    }

    private static MacAlgorithm macAlgorithmFor(int secretLength) {
        if (secretLength >= 64) {
            return MacAlgorithm.HS512;
        }
        if (secretLength >= 48) {
            return MacAlgorithm.HS384;
        }
        return MacAlgorithm.HS256;
    }

    private static String jcaAlgorithmFor(MacAlgorithm macAlgorithm) {
        return switch (macAlgorithm) {
            case HS512 -> "HmacSHA512";
            case HS384 -> "HmacSHA384";
            case HS256 -> "HmacSHA256";
        };
    }
}
