package com.monitoring.logforwarder.repository.postgresql;

import com.monitoring.logforwarder.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsernameAndPassword(String username, String password);

    List<User> findByRole(String role);

    @Query("SELECT u FROM User u WHERE u.isActive = true AND u.isLocked = false")
    List<User> findActiveUsers();

    @Query("SELECT u FROM User u WHERE u.isActive = false")
    List<User> findInactiveUsers();

    @Query("SELECT u FROM User u WHERE u.failedLoginAttempts > 5")
    List<User> findLockedUsers();

    @Query("SELECT COUNT(u) FROM User u WHERE u.role = :role")
    Long countByRole(@Param("role") String role);

    @Query("SELECT u FROM User u WHERE u.lastLogin < :cutoffDate")
    List<User> findInactiveUsersSince(@Param("cutoffDate") LocalDateTime cutoffDate);

    @Query("SELECT u FROM User u WHERE u.lastPasswordChange IS NULL OR u.lastPasswordChange < :cutoffDate")
    List<User> findUsersWithOldPasswords(@Param("cutoffDate") LocalDateTime cutoffDate);

    @Query("SELECT COUNT(u) FROM User u WHERE u.isActive = true")
    Long countActiveUsers();
}
