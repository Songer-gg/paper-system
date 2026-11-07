package cn.paperhub.security;

import cn.paperhub.auth.entity.UserAccount;
import cn.paperhub.support.MutableClock;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    private SecurityProperties properties;
    private MutableClock clock;
    private JwtService jwt;
    private UserAccount account;

    @BeforeEach void setUp() {
        properties = new SecurityProperties();
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        properties.setJwtSecret(Base64.getEncoder().encodeToString(key));
        clock = new MutableClock();
        jwt = new JwtService(properties, clock);
        account = new UserAccount();
        account.setId(1L);
        account.setRole("user");
        account.setTokenVersion(3L);
    }
    @Test void tokenContainsOnlySessionIdentityAndExpiresAt24Hours() {
        String token = jwt.issue(account);
        assertEquals("1", jwt.parse(token).getSubject());
        assertEquals(3, jwt.parse(token).get("ver"));
        assertFalse(jwt.parse(token).containsKey("password"));
        clock.advance(Duration.ofHours(24));
        assertThrows(JwtException.class, () -> jwt.parse(token));
    }
    @Test void invalidSignatureAndMalformedTokensAreRejected() {
        String token = jwt.issue(account);
        byte[] otherKey = new byte[32];
        new SecureRandom().nextBytes(otherKey);
        properties.setJwtSecret(Base64.getEncoder().encodeToString(otherKey));
        JwtService other = new JwtService(properties, clock);
        assertThrows(JwtException.class, () -> other.parse(token));
        assertThrows(JwtException.class, () -> jwt.parse("not.a.jwt"));
    }
    @Test void missingExpirationAndWrongAudienceAreRejected() {
        String noExpiry = Jwts.builder().setSubject("1").setIssuer("paperhub").setAudience("paperhub-web")
                .setIssuedAt(Date.from(clock.instant()))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(properties.getJwtSecret())), SignatureAlgorithm.HS256).compact();
        assertThrows(JwtException.class, () -> jwt.parse(noExpiry));
        String wrongAudience = Jwts.builder().setSubject("1").setIssuer("paperhub").setAudience("another-app")
                .setExpiration(Date.from(clock.instant().plusSeconds(100)))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(properties.getJwtSecret())), SignatureAlgorithm.HS256).compact();
        assertThrows(JwtException.class, () -> jwt.parse(wrongAudience));
    }
    @Test void unsafeIdsAndMissingSecretFailClosed() {
        account.setId(JwtService.MAX_SAFE_ID + 1);
        assertThrows(IllegalStateException.class, () -> jwt.issue(account));
        properties.setJwtSecret("");
        assertThrows(IllegalStateException.class, () -> new JwtService(properties, clock));
    }
}
