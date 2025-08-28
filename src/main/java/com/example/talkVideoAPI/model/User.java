package com.example.talkVideoAPI.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "oauth_id", unique = true)
    private String oauthId;

    private String name;

    @Column(unique = true)
    private String email;

    @Column(name = "picture")
    private String picture;

    @Column(name = "language_native")
    private String languageFluent;

    @Column(name = "language_target")
    private String languageTarget;

    @Column(name = "created_at")
    private LocalDateTime created_at;

    public User() {}

    public User(String oauthId, String name, String email, String picture) {
        this.oauthId = oauthId;
        this.name = name;
        this.email = email;
        this.picture = picture;
        this.created_at = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getOauthId() { return oauthId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPicture() { return picture; }
    public String getLanguageFluent() { return languageFluent; }
    public void setLanguageFluent(String languageFluent) { this.languageFluent = languageFluent; }
    public String getLanguageTarget() { return languageTarget; }
    public void setLanguageTarget(String languageTarget) { this.languageTarget = languageTarget; }
    public LocalDateTime getCreated_at() { return created_at; }
}
