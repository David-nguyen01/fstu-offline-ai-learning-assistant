package com.courseqa.repository;

import com.courseqa.model.entity.PasswordResetToken;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    Optional<PasswordResetToken> findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(
            String tokenHash, LocalDateTime now);

    boolean existsByUserIdAndUsedAtIsNullAndCreatedAtAfter(UUID userId, LocalDateTime threshold);

    List<PasswordResetToken> findByUserIdAndUsedAtIsNull(UUID userId);
}
