package com.courseqa.service;

import com.courseqa.model.dto.AuthDto;
import com.courseqa.model.entity.User;
import com.courseqa.model.entity.UserRole;
import com.courseqa.model.entity.PasswordResetToken;
import com.courseqa.repository.PasswordResetTokenRepository;
import com.courseqa.repository.UserRepository;
import com.courseqa.repository.UserRoleRepository;
import com.courseqa.security.JwtService;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);
    private static final List<String> ALLOWED_ROLES = List.of("ADMIN", "USER", "TEACHER", "STUDENT", "RESEARCHER");
    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final SubscriptionService subscriptionService;
    private final String mailFrom;
    private final String mailHost;
    private final String mailUsername;
    private final String mailPassword;
    private final String frontendBaseUrl;

    public AuthService(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            ObjectProvider<JavaMailSender> mailSenderProvider,
            SubscriptionService subscriptionService,
            @Value("${app.mail.from:no-reply@courseqa.local}") String mailFrom,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${spring.mail.username:}") String mailUsername,
            @Value("${spring.mail.password:}") String mailPassword,
            @Value("${app.frontend.base-url:http://localhost:5173}") String frontendBaseUrl
    ) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.mailSenderProvider = mailSenderProvider;
        this.subscriptionService = subscriptionService;
        this.mailFrom = mailFrom;
        this.mailHost = mailHost;
        this.mailUsername = mailUsername;
        this.mailPassword = mailPassword;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public AuthDto.AuthResponse register(AuthDto.RegisterRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Register request is required.");
        }
        String email = normalizeEmail(request.email);

        if (request.fullName == null || request.fullName.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Full name is required.");
        }

        validateNewPassword(request.password);

        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered.");
        }

        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setFullName(request.fullName.trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password));
        user.setIsActive(true);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        User savedUser = userRepository.save(user);
        UserRole role = new UserRole();
        role.setUserId(savedUser.getUserId());
        //role.setRoleName(normalizeRole(request.roleName));
        role.setRoleName("STUDENT");
        role.setPermissionJson("{}");
        role.setAssignedAt(now);
        role.setIsActive(true);
        userRoleRepository.save(role);
        subscriptionService.ensureFreeSubscription(savedUser.getUserId());

        return buildAuthResponse(savedUser);
    }

    public AuthDto.AuthResponse login(AuthDto.LoginRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Login request is required.");
        }
        String email = normalizeEmail(request.email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password."));

        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account is inactive.");
        }

        if (request.password == null || !passwordEncoder.matches(request.password, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        }

        user.setLastLoginAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        User savedUser = userRepository.save(user);

        return buildAuthResponse(savedUser);
    }

    @Transactional
    public void forgotPassword(AuthDto.ForgotPasswordRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Forgot password request is required.");
        }

        if (isBlank(mailHost) || isBlank(mailUsername) || isBlank(mailPassword)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Email service is not configured.");
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Email service is not configured.");
        }

        String email = normalizeEmail(request.email);
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || Boolean.FALSE.equals(user.getIsActive())) return;

        LocalDateTime now = LocalDateTime.now();
        if (passwordResetTokenRepository.existsByUserIdAndUsedAtIsNullAndCreatedAtAfter(
                user.getUserId(), now.minusMinutes(1))) {
            return;
        }

        for (PasswordResetToken previous : passwordResetTokenRepository.findByUserIdAndUsedAtIsNull(user.getUserId())) {
            previous.setUsedAt(now);
        }

        String rawToken = generateResetToken();
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUserId(user.getUserId());
        resetToken.setTokenHash(hashToken(rawToken));
        resetToken.setCreatedAt(now);
        resetToken.setExpiresAt(now.plusMinutes(30));
        passwordResetTokenRepository.save(resetToken);

        try {
            sendPasswordResetEmail(mailSender, user, rawToken);
        } catch (MailException error) {
            LOGGER.warn("Could not send reset password email to {}.", user.getEmail(), error);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Could not send reset password email.");
        }
    }

    @Transactional
    public void resetPassword(AuthDto.ResetPasswordRequest request) {
        if (request == null || isBlank(request.token)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reset token is required.");
        }
        validateNewPassword(request.newPassword);

        LocalDateTime now = LocalDateTime.now();
        PasswordResetToken token = passwordResetTokenRepository
                .findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(hashToken(request.token.trim()), now)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Reset link is invalid or has expired."));
        User user = userRepository.findById(token.getUserId())
                .filter(candidate -> !Boolean.FALSE.equals(candidate.getIsActive()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Reset link is invalid or has expired."));

        user.setPasswordHash(passwordEncoder.encode(request.newPassword));
        user.setLastLogoutAt(now);
        user.setUpdatedAt(now);
        token.setUsedAt(now);
        userRepository.save(user);
        passwordResetTokenRepository.save(token);
    }

    public void logout(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        user.setLastLogoutAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    @Transactional
    public void changePassword(UUID userId, AuthDto.ChangePasswordRequest request) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
        }
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Change password request is required.");
        }
        if (isBlank(request.currentPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is required.");
        }
        validateNewPassword(request.newPassword);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        if (!passwordEncoder.matches(request.currentPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current password is incorrect.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword));
        user.setLastLogoutAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    public List<AuthDto.UserResponse> getUsers() {
        return userRepository.findAll().stream()
                .map(user -> new AuthDto.UserResponse(user, getRoleNames(user.getUserId())))
                .toList();
    }

    public List<String> getRoles(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
        }
        return getRoleNames(userId);
    }

    @Transactional
    public AuthDto.UserResponse updateUserRole(
            UUID userId, AuthDto.UpdateUserRoleRequest request, UUID requesterId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        String roleName = normalizeRole(request == null ? null : request.roleName);
        if (userId.equals(requesterId) && !"ADMIN".equals(roleName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Admin cannot remove their own admin role.");
        }
        if (isAdmin(userId) && !"ADMIN".equals(roleName) && countActiveAdmins() <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The system must keep at least one active admin.");
        }
        LocalDateTime now = LocalDateTime.now();

        List<UserRole> roles = userRoleRepository.findByUserId(userId);
        for (UserRole role : roles) {
            role.setIsActive(false);
        }
        userRoleRepository.saveAll(roles);

        UserRole role = new UserRole();
        role.setUserId(userId);
        role.setRoleName(roleName);
        role.setPermissionJson(roleName.equals("ADMIN") ? "{\"all\":true}" : "{}");
        role.setAssignedAt(now);
        role.setIsActive(true);
        userRoleRepository.save(role);

        user.setUpdatedAt(now);
        userRepository.save(user);

        return new AuthDto.UserResponse(user, getRoleNames(userId));
    }

    @Transactional
    public void deleteUser(UUID userId, UUID requesterId) {
        if (userId == null || requesterId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId and requesterId are required.");
        }
        if (userId.equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin cannot delete their own account.");
        }
        if (!isAdmin(requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admin can delete users.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        if (isAdmin(userId) && countActiveAdmins() <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The system must keep at least one active admin.");
        }
        LocalDateTime now = LocalDateTime.now();
        user.setIsActive(false);
        user.setLastLogoutAt(now);
        user.setUpdatedAt(now);
        List<UserRole> roles = userRoleRepository.findByUserId(userId);
        roles.forEach(role -> role.setIsActive(false));
        userRoleRepository.saveAll(roles);
        userRepository.save(user);
    }

    private AuthDto.AuthResponse buildAuthResponse(User user) {
        List<String> roles = getRoleNames(user.getUserId());
        return new AuthDto.AuthResponse(jwtService.issue(user.getUserId(), user.getEmail(), roles), user, roles);
    }

    private List<String> getRoleNames(UUID userId) {
        return userRoleRepository.findByUserIdAndIsActiveTrue(userId).stream()
                .map(UserRole::getRoleName)
                .toList();
    }

    public boolean isAdmin(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Requester user not found.");
        }
        return getRoleNames(userId).stream()
                .anyMatch(role -> "ADMIN".equalsIgnoreCase(role));
    }

    private long countActiveAdmins() {
        return userRoleRepository.countByRoleNameIgnoreCaseAndIsActiveTrueAndUserIsActive("ADMIN");
    }

    private String generateResetToken() {
        byte[] bytes = new byte[32];
        TOKEN_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable.", impossible);
        }
    }

    private void sendPasswordResetEmail(JavaMailSender mailSender, User user, String rawToken) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(user.getEmail());
        message.setSubject("FStu password reset");
        message.setText("""
                Xin chào %s,

                Hệ thống nhận được yêu cầu đặt lại mật khẩu FStu của bạn.
                Mở liên kết sau trong vòng 30 phút:

                %s/reset-password?token=%s

                Nếu bạn không gửi yêu cầu này, hãy bỏ qua email. Mật khẩu hiện tại vẫn được giữ nguyên.
                """.formatted(user.getFullName(), frontendBaseUrl, rawToken));
        mailSender.send(message);
    }

    private void validateNewPassword(String password) {
        if (isBlank(password)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password is required.");
        }
        if (password.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must be at least 8 characters.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalizeEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRole(String roleName) {
        String normalizedRole = roleName == null || roleName.isBlank()
                ? "STUDENT"
                : roleName.trim().toUpperCase(Locale.ROOT);

        if (!ALLOWED_ROLES.contains(normalizedRole)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role.");
        }

        return normalizedRole.equals("USER") ? "STUDENT" : normalizedRole;
    }
}
