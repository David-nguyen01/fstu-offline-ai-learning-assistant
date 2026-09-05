package com.courseqa.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-minutes:120}") long expirationMinutes
    ) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expirationMinutes = expirationMinutes;
    }

    public String issue(UUID userId, String email, List<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("roles", roles)
                // JWT's registered iat is second-precision. Keep a millisecond claim
                // so a login immediately after logout is not mistaken for an old token.
                .claim("issued_at_ms", now.toEpochMilli())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    public JwtPrincipal parse(String token) {
        Claims claims = claims(token);
        List<?> rawRoles = claims.get("roles", List.class);
        List<String> roles = rawRoles == null ? List.of() : rawRoles.stream().map(String::valueOf).toList();
        return new JwtPrincipal(UUID.fromString(claims.getSubject()), claims.get("email", String.class), roles);
    }

    public Instant issuedAt(String token) {
        Claims claims = claims(token);
        Number issuedAtMillis = claims.get("issued_at_ms", Number.class);
        if (issuedAtMillis != null) return Instant.ofEpochMilli(issuedAtMillis.longValue());
        Date issuedAt = claims.getIssuedAt();
        if (issuedAt == null) throw new JwtException("JWT is missing issued-at time.");
        return issuedAt.toInstant();
    }

    private Claims claims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
