package cn.paperhub.security;

import cn.paperhub.auth.entity.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    public static final long MAX_SAFE_ID = 9007199254740991L;
    private static final String ISSUER = "paperhub";
    private static final String AUDIENCE = "paperhub-web";
    private final Key key;
    private final JwtParser parser;
    private final Clock clock;
    private final Duration ttl;

    public JwtService(SecurityProperties properties, Clock clock) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(properties.getJwtSecret());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new IllegalStateException("PAPERHUB_JWT_SECRET must be a Base64 random key");
        }
        if (bytes.length < 32) throw new IllegalStateException("JWT key must contain at least 32 decoded bytes");
        this.key = Keys.hmacShaKeyFor(bytes);
        this.clock = clock;
        this.ttl = properties.getTokenTtl();
        if (ttl == null || ttl.getSeconds() < 1 || ttl.compareTo(Duration.ofHours(24)) > 0) {
            throw new IllegalStateException("Token TTL must be between one second and 24 hours");
        }
        this.parser = Jwts.parserBuilder().setSigningKey(key)
                .requireIssuer(ISSUER).requireAudience(AUDIENCE)
                .setClock(() -> Date.from(clock.instant())).build();
    }

    public String issue(UserAccount account) {
        if (account.getId() == null || account.getId() < 1 || account.getId() > MAX_SAFE_ID
                || account.getTokenVersion() == null || account.getTokenVersion() < 0
                || !("user".equals(account.getRole()) || "admin".equals(account.getRole()))) {
            throw new IllegalStateException("Account session fields are invalid");
        }
        Instant now = clock.instant();
        return Jwts.builder().setSubject(account.getId().toString()).setIssuer(ISSUER)
                .setAudience(AUDIENCE).setId(UUID.randomUUID().toString())
                .setIssuedAt(Date.from(now)).setExpiration(Date.from(now.plus(ttl)))
                .claim("ver", account.getTokenVersion()).claim("role", account.getRole())
                .signWith(key, SignatureAlgorithm.HS256).compact();
    }

    public Claims parse(String token) {
        Claims claims = parser.parseClaimsJws(token).getBody();
        if (claims.getExpiration() == null || claims.getIssuedAt() == null
                || claims.getExpiration().toInstant().isAfter(claims.getIssuedAt().toInstant().plus(Duration.ofHours(24)))
                || claims.getIssuedAt().toInstant().isAfter(clock.instant())
                || claims.getExpiration().toInstant().compareTo(clock.instant()) <= 0) {
            throw new JwtException("Invalid session timestamps");
        }
        return claims;
    }
}
