package com.example.spring_boot_project_api.repository;

import com.example.spring_boot_project_api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByUsername(String username);

	boolean existsByUsername(String username);

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);
}
