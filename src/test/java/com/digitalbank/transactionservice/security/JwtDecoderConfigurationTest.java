package com.digitalbank.transactionservice.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtDecoderConfigurationTest {

    private static final String JWT_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String JWT_ISSUER = "digital-bank-auth";

    @Test
    void decodesAuthServiceHmacToken() {
        var environment = new MockEnvironment()
                .withProperty("auth.jwt.secret", JWT_SECRET)
                .withProperty("auth.jwt.issuer", JWT_ISSUER);
        var decoder = new JwtDecoderConfiguration().jwtDecoder(environment);
        var now = Instant.now();
        var signingKey = new OctetSequenceKey.Builder(Base64.getDecoder().decode(JWT_SECRET))
                .algorithm(JWSAlgorithm.HS256)
                .keyID("test")
                .build();
        var token = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)))
                .encode(JwtEncoderParameters.from(
                        org.springframework.security.oauth2.jwt.JwsHeader.with(MacAlgorithm.HS256).build(),
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
}
