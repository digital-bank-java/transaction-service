package com.digitalbank.transactionservice.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.time.Instant;
import java.util.Base64;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtDecoderConfigurationTest {

    private static final String JWT_ISSUER = "digital-bank-auth";

    @ParameterizedTest
    @MethodSource("hmacSecrets")
    void decodesAuthServiceHmacToken(int secretLength, MacAlgorithm macAlgorithm) {
        String jwtSecret = Base64.getEncoder().encodeToString(new byte[secretLength]);
        var environment = new MockEnvironment()
                .withProperty("auth.jwt.secret", jwtSecret)
                .withProperty("auth.jwt.issuer", JWT_ISSUER);
        var decoder = new JwtDecoderConfiguration().jwtDecoder(environment);
        var now = Instant.now();
        var signingKey = new OctetSequenceKey.Builder(Base64.getDecoder().decode(jwtSecret))
                .algorithm(JWSAlgorithm.parse(macAlgorithm.getName()))
                .keyID("test")
                .build();
        var token = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)))
                .encode(JwtEncoderParameters.from(
                        org.springframework.security.oauth2.jwt.JwsHeader.with(macAlgorithm).build(),
                        JwtClaimsSet.builder()
                        .issuer(JWT_ISSUER)
                        .subject("transfer-orchestrator")
                        .issuedAt(now.minusSeconds(1))
                        .expiresAt(now.plusSeconds(60))
                        .claim("scope", "transfer.internal")
                        .build()))
                .getTokenValue();

        assertThat(decoder.decode(token).getSubject()).isEqualTo("transfer-orchestrator");
    }

    private static Stream<Arguments> hmacSecrets() {
        return Stream.of(
                Arguments.of(32, MacAlgorithm.HS256),
                Arguments.of(48, MacAlgorithm.HS384),
                Arguments.of(64, MacAlgorithm.HS512));
    }
}
