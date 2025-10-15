package com.bni.orange.users.repository;

import com.bni.orange.users.model.entity.UserProfileView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserProfileViewRepository extends JpaRepository<UserProfileView, UUID> {
}
