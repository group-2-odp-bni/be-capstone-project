package com.bni.orange.authentication.repository;

import org.springframework.data.jpa.repository.JpaRepository;


public interface AuthRepository extends JpaRepository<String, String> {
}
