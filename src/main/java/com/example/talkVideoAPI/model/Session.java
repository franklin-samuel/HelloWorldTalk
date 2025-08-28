package com.example.talkVideoAPI.model;

import jakarta.persistence.*;
import org.springframework.cglib.core.Local;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_session")
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user1_id", nullable = false)
    private User user1;

    @ManyToOne
    @JoinColumn(name = "user2_id")
    private User user2;

    @Column(name = "started_at")
    private LocalDateTime started_at;

    @Column(name = "ended_at")
    private LocalDateTime ended_at;

    @Enumerated(EnumType.STRING)
     private StatusSession status;

    public Session() {}

    public Session(User user1) {
        this.user1 = user1;
        this.status = StatusSession.WAITING;
        this.started_at = LocalDateTime.now();
    }

    public void addUser2(User user2) {
        this.user2 = user2;
        this.status = StatusSession.ACTIVE;
    }

    public void endSession() {
        this.status = StatusSession.ENDED;
        this.ended_at = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public User getUser1() { return user1; }
    public void setUser1(User user1) { this.user1 = user1; }
    public User getUser2() { return user2; }
    public void setUser2(User user2) { this.user2 = user2; }
    public LocalDateTime getStarted_at() { return started_at; }
    public void setStarted_at(LocalDateTime started_at) { this.started_at = started_at; }
    public LocalDateTime getEnded_at() { return ended_at; }
    public void setEnded_at(LocalDateTime ended_at) { this.ended_at = ended_at; }
    public StatusSession getStatus() { return status; }
    public void setStatus(StatusSession status) { this.status = status; }
}
