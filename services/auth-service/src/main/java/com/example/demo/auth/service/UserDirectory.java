package com.example.demo.auth.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.auth.domain.UserEntity;
import com.example.demo.auth.domain.UserRepository;

/** Postgres-backed user directory: authentication, listing, lookup, and self-service registration. */
@Service
public class UserDirectory {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserDirectory(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public UserEntity authenticate(String username, String password) {
        return repository.findByUsername(username)
                .filter(candidate -> passwordEncoder.matches(password, candidate.getPasswordHash()))
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
    }

    @Transactional
    public UserEntity register(String username, String email, String rawPassword) {
        if (repository.existsByUsername(username)) {
            // Intentional for the demo: the API tells the client the username is already taken.
            // A production system might hide that detail to reduce account enumeration, but the demo
            // favors a more explicit registration UX.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already taken");
        }
        if (repository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        UserEntity user = new UserEntity(
                UUID.randomUUID().toString(),
                username,
                email,
                username,
                passwordEncoder.encode(rawPassword));
        return repository.save(user);
    }

    @Transactional(readOnly = true)
    public List<UserEntity> all() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<UserEntity> byId(String id) {
        return repository.findById(id);
    }
}
