package com.courseqa.security;

import com.courseqa.repository.UserRepository;
import com.courseqa.repository.UserRoleRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.ZoneId;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final ObjectProvider<UserRepository> userRepositoryProvider;
    private final ObjectProvider<UserRoleRepository> userRoleRepositoryProvider;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            ObjectProvider<UserRepository> userRepositoryProvider,
            ObjectProvider<UserRoleRepository> userRoleRepositoryProvider) {
        this.jwtService = jwtService;
        this.userRepositoryProvider = userRepositoryProvider;
        this.userRoleRepositoryProvider = userRoleRepositoryProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                String token = header.substring(7);
                JwtPrincipal tokenPrincipal = jwtService.parse(token);
                var userRepository = userRepositoryProvider.getIfAvailable();
                var userRoleRepository = userRoleRepositoryProvider.getIfAvailable();
                if (userRepository == null || userRoleRepository == null) {
                    authenticate(tokenPrincipal, tokenPrincipal.roles());
                    chain.doFilter(request, response);
                    return;
                }
                var user = userRepository.findById(tokenPrincipal.userId())
                        .filter(candidate -> !Boolean.FALSE.equals(candidate.getIsActive()))
                        .orElseThrow(() -> new IllegalArgumentException("Inactive or missing user."));
                if (user.getLastLogoutAt() != null
                        && !jwtService.issuedAt(token).isAfter(
                                user.getLastLogoutAt().atZone(ZoneId.systemDefault()).toInstant())) {
                    throw new IllegalArgumentException("JWT was revoked.");
                }
                var currentRoles = userRoleRepository.findByUserIdAndIsActiveTrue(user.getUserId()).stream()
                        .map(role -> role.getRoleName().toUpperCase())
                        .distinct()
                        .toList();
                authenticate(new JwtPrincipal(user.getUserId(), user.getEmail(), currentRoles), currentRoles);
            } catch (JwtException | IllegalArgumentException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }

    private void authenticate(JwtPrincipal principal, java.util.List<String> roles) {
        var authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }
}
