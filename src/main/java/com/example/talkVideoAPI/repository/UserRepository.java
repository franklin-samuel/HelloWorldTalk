package com.example.talkVideoAPI.repository;

import com.example.talkVideoAPI.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository {
    Optional<User> findByEmail(String email);
    Optional<User> findById(Long id);
}
