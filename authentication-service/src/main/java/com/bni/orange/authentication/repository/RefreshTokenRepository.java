package com.bni.orange.authentication.repository;

import com.bni.orange.authentication.model.entity.RefreshToken;
import com.bni.orange.authentication.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHashAndIsRevokedFalse(String tokenHash);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.isRevoked = true, rt.revokedAt = CURRENT_TIMESTAMP WHERE rt.user = :user")
    void revokeAllByUser(@Param("user") User user);

    List<RefreshToken> findAllByUserAndIsRevokedFalse(User user);
}