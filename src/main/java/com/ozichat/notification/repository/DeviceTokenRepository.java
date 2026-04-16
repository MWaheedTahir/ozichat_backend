package com.ozichat.notification.repository;

import com.ozichat.notification.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    List<DeviceToken> findByUserIdAndIsActiveTrue(Long userId);

    Optional<DeviceToken> findByUserIdAndToken(Long userId, String token);

    boolean existsByUserIdAndToken(Long userId, String token);

    @Modifying
    @Query("UPDATE DeviceToken dt SET dt.isActive = false WHERE dt.userId = :userId AND dt.token = :token")
    void deactivateToken(Long userId, String token);

    @Modifying
    @Query("UPDATE DeviceToken dt SET dt.isActive = false WHERE dt.userId = :userId")
    void deactivateAllByUserId(Long userId);
}
