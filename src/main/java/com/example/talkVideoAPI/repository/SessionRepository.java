package com.example.talkVideoAPI.repository;

import com.example.talkVideoAPI.model.Session;
import com.example.talkVideoAPI.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.talkVideoAPI.model.StatusSession;

import java.util.Optional;

public interface SessionRepository extends JpaRepository {
    Optional<Session> findByUser1andStatus(User user, StatusSession status);
}
