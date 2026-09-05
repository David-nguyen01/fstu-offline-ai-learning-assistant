package com.courseqa.repository;

import com.courseqa.model.entity.UserRole;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {
    List<UserRole> findByUserIdAndIsActiveTrue(UUID userId);

    List<UserRole> findByUserId(UUID userId);

    @Query("""
            select count(distinct role.userId) from UserRole role, User user
            where role.userId = user.userId and lower(role.roleName) = lower(:roleName)
              and role.isActive = true and user.isActive = true
            """)
    long countByRoleNameIgnoreCaseAndIsActiveTrueAndUserIsActive(@Param("roleName") String roleName);
}
