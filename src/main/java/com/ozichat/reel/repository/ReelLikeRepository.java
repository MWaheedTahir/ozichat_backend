package com.ozichat.reel.repository;

import com.ozichat.reel.entity.ReelLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReelLikeRepository extends JpaRepository<ReelLike, Long> {

    boolean existsByReelIdAndUserId(String reelId, Long userId);

    Optional<ReelLike> findByReelIdAndUserId(String reelId, Long userId);

    long countByReelId(String reelId);

    @Modifying
    @Query("DELETE FROM ReelLike rl WHERE rl.reelId = :reelId AND rl.userId = :userId")
    void deleteByReelIdAndUserId(@Param("reelId") String reelId, @Param("userId") Long userId);
}
