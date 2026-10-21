package com.iot.domain.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String fullname;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 50)
    private String msv;

    @Column(length = 255)
    private String email;

    private String github;
    private String postman;
    private String figma;
    private String pdf;

    protected User() {
    }

    public User(String fullname, String username, String password, String msv, String email) {
        this.fullname = fullname;
        this.username = username;
        this.password = password;
        this.msv = msv;
        this.email = email;
    }

    public Integer getId() { return id; }
    public String getMsv() { return msv; }
    public String getFullname() { return fullname; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getEmail() { return email; }
    public String getGithub() { return github; }
    public String getPostman() { return postman; }
    public String getFigma() { return figma; }
    public String getPdf() { return pdf; }
}
