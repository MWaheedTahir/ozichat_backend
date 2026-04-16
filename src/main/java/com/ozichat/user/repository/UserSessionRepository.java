package com.ozichat.user.repository;

import com.ozichat.user.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findByRefreshTokenHashAndIsRevokedFalse(String hash);

    List<UserSession> findByUserIdAndIsRevokedFalse(Long userId);

    @Modifying
    @Query("UPDATE UserSession s SET s.isRevoked = true WHERE s.userId = :userId")
    void revokeAllByUserId(Long userId);

    @Modifying
    @Query("UPDATE UserSession s SET s.isRevoked = true WHERE s.id = :sessionId")
    void revokeById(Long sessionId);
}
