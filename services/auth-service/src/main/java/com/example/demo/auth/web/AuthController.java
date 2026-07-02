package com.example.demo.auth.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.auth.domain.UserEntity;
import com.example.demo.auth.service.TokenIssuer;
import com.example.demo.auth.service.UserDirectory;
import com.example.demo.auth.web.AuthDtos.LoginRequest;
import com.example.demo.auth.web.AuthDtos.LoginResponse;
import com.example.demo.auth.web.AuthDtos.RegisterRequest;
import com.example.demo.auth.web.AuthDtos.UserResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserDirectory users;
    private final TokenIssuer tokenIssuer;

    public AuthController(UserDirectory users, TokenIssuer tokenIssuer) {
        this.users = users;
        this.tokenIssuer = tokenIssuer;
    }

    /** Public: exchange demo credentials for a JWT. */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        UserEntity user = users.authenticate(request.username(), request.password());
        return new LoginResponse(tokenIssuer.issue(user.getUsername()), user.getUsername());
    }

    /** Public: self-service registration. On success the new user is logged in (a token is returned). */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse register(@Valid @RequestBody RegisterRequest request) {
        UserEntity user = users.register(request.username(), request.email(), request.password());
        return new LoginResponse(tokenIssuer.issue(user.getUsername()), user.getUsername());
    }

    /** Secured: requires a valid JWT. */
    @GetMapping("/users")
    public List<UserResponse> users() {
        return users.all().stream().map(UserResponse::from).toList();
    }

    @GetMapping("/users/{id}")
    public UserResponse user(@PathVariable String id) {
        return users.byId(id).map(UserResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No user " + id));
    }
}
