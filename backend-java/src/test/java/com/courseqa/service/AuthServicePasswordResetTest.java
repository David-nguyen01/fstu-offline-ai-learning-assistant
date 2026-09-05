package com.courseqa.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.courseqa.model.dto.AuthDto;
import com.courseqa.model.entity.PasswordResetToken;
import com.courseqa.model.entity.User;
import com.courseqa.repository.PasswordResetTokenRepository;
import com.courseqa.repository.UserRepository;
import com.courseqa.repository.UserRoleRepository;
import com.courseqa.security.JwtService;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServicePasswordResetTest {
    private final UserRepository users = mock(UserRepository.class);
    private final UserRoleRepository roles = mock(UserRoleRepository.class);
    private final PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final JwtService jwt = mock(JwtService.class);
    private final JavaMailSender mail = mock(JavaMailSender.class);
    private final SubscriptionService subscriptions = mock(SubscriptionService.class);
    private AuthService service;
    private User user;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ObjectProvider<JavaMailSender> mailProvider = mock(ObjectProvider.class);
        when(mailProvider.getIfAvailable()).thenReturn(mail);
        service = new AuthService(
                users, roles, tokens, encoder, jwt, mailProvider, subscriptions,
                "no-reply@fstu.local", "smtp.local", "mailer", "secret",
                "http://localhost:5173");
        user = new User();
        user.setUserId(UUID.randomUUID());
        user.setFullName("Student");
        user.setEmail("student@example.com");
        user.setPasswordHash("unchanged-hash");
        user.setIsActive(true);
    }

    @Test
    void forgotPasswordSendsOneTimeLinkWithoutChangingCurrentPassword() {
        when(users.findByEmail("student@example.com")).thenReturn(Optional.of(user));
        when(tokens.existsByUserIdAndUsedAtIsNullAndCreatedAtAfter(eq(user.getUserId()), any()))
                .thenReturn(false);

        AuthDto.ForgotPasswordRequest request = new AuthDto.ForgotPasswordRequest();
        request.email = "Student@Example.com";
        service.forgotPassword(request);

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokens).save(tokenCaptor.capture());
        assertEquals(64, tokenCaptor.getValue().getTokenHash().length());
        assertEquals("unchanged-hash", user.getPasswordHash());
        verify(users, never()).save(any(User.class));

        ArgumentCaptor<SimpleMailMessage> mailCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(mailCaptor.capture());
        assertTrue(mailCaptor.getValue().getText().contains("/reset-password?token="));
    }

    @Test
    void resetPasswordConsumesTokenAndRevokesExistingSessions() {
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(user.getUserId());
        token.setTokenHash("hash");
        token.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        when(tokens.findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(any(), any()))
                .thenReturn(Optional.of(token));
        when(users.findById(user.getUserId())).thenReturn(Optional.of(user));
        when(encoder.encode("new-password")).thenReturn("new-hash");

        AuthDto.ResetPasswordRequest request = new AuthDto.ResetPasswordRequest();
        request.token = "one-time-token";
        request.newPassword = "new-password";
        service.resetPassword(request);

        assertEquals("new-hash", user.getPasswordHash());
        assertNotNull(user.getLastLogoutAt());
        assertNotNull(token.getUsedAt());
        verify(users).save(user);
        verify(tokens).save(token);
    }
}
