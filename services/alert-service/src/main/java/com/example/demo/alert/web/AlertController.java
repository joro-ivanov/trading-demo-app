package com.example.demo.alert.web;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.alert.domain.AlertEntity;
import com.example.demo.alert.domain.AlertStatus;
import com.example.demo.alert.service.AlertService;

import jakarta.validation.Valid;

/**
 * Manages the caller's price alerts. Triggered alerts are not polled for: they are pushed over
 * WebSocket by notification-service ({@code /user/queue/alerts}). {@code authentication.getName()}
 * is the JWT subject, so a user can only ever see or change their own alerts.
 */
@RestController
@RequestMapping("/alerts")
public class AlertController {

    private final AlertService alerts;

    public AlertController(AlertService alerts) {
        this.alerts = alerts;
    }

    @PostMapping
    public ResponseEntity<AlertResponse> create(@Valid @RequestBody CreateAlertRequest request,
                                                Authentication authentication) {
        AlertEntity alert = alerts.create(authentication.getName(), request.symbol(), request.condition(),
                request.threshold());
        return ResponseEntity.created(URI.create("/alerts/" + alert.getId())).body(AlertResponse.from(alert));
    }

    @GetMapping
    public List<AlertResponse> list(@RequestParam Optional<AlertStatus> status, Authentication authentication) {
        return alerts.list(authentication.getName(), status).stream().map(AlertResponse::from).toList();
    }

    @GetMapping("/{id}")
    public AlertResponse get(@PathVariable UUID id, Authentication authentication) {
        return AlertResponse.from(alerts.get(authentication.getName(), id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, Authentication authentication) {
        alerts.delete(authentication.getName(), id);
    }
}
