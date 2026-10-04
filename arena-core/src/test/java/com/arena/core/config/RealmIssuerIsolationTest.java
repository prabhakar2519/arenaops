package com.arena.core.config;

import com.arena.core.exception.ApiErrors;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RealmIssuerIsolationTest {
  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void correctlySignedTokenFromAnotherRealmIsRejected() throws Exception {
    var key = new RSAKeyGenerator(2048).keyID("test-key").generate();
    for (String issuer : new String[]{"http://localhost:9091/auth/realms/arena-dev",
        "https://sit.arenaops.in/auth/realms/arena-sit", "https://arenaops.in/auth/realms/arena"}) {
      var config = new SecurityConfig(mock(CustomerAccessFilter.class), mock(ApiErrors.class));
      ReflectionTestUtils.setField(config, "jwkSetUri", "http://unused.test/jwks");
      ReflectionTestUtils.setField(config, "issuerUri", issuer);
      var decoder = config.jwtDecoder();
      // Replace only remote key retrieval with a local public key; exercise actual signature/issuer decoding.
      var processor = (ConfigurableJWTProcessor) ReflectionTestUtils.getField(decoder, "jwtProcessor");
      processor.setJWSKeySelector(new JWSVerificationKeySelector(JWSAlgorithm.RS256,
          new ImmutableJWKSet(new JWKSet(key.toPublicJWK()))));
      for (String tokenIssuer : new String[]{issuer, "https://other.test/auth/realms/other"}) {
        var now = Instant.now();
        var claims = new JWTClaimsSet.Builder().subject("test-user").issuer(tokenIssuer)
            .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(300))).build();
        var token = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims);
        token.sign(new RSASSASigner(key));
        if (issuer.equals(tokenIssuer)) assertEquals("test-user", decoder.decode(token.serialize()).getSubject());
        else assertThrows(JwtValidationException.class, () -> decoder.decode(token.serialize()));
      }
    }
  }
}
