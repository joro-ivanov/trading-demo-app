package com.example.demo.risk.web;

import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.risk.service.RiskCheckService;
import com.example.demo.risk.service.RiskLimitService;
import com.example.demo.risk.web.RiskDtos.RiskCheckRequest;
import com.example.demo.risk.web.RiskDtos.RiskCheckResponse;
import com.example.demo.risk.web.RiskDtos.RiskLimitsResponse;
import com.example.demo.risk.web.RiskDtos.UpdateRiskLimitsRequest;

/** Risk limits and the pre-trade check, always for the authenticated user (the JWT subject). */
@RestController
@RequestMapping("/risk")
public class RiskController {

    private final RiskLimitService limits;
    private final RiskCheckService checks;

    public RiskController(RiskLimitService limits, RiskCheckService checks) {
        this.limits = limits;
        this.checks = checks;
    }

    @GetMapping("/limits")
    public RiskLimitsResponse limits(Authentication authentication) {
        return limits.limitsFor(authentication.getName());
    }

    @PutMapping("/limits")
    public RiskLimitsResponse updateLimits(@Valid @RequestBody UpdateRiskLimitsRequest request,
                                           Authentication authentication) {
        return limits.update(authentication.getName(), request);
    }

    /** Always 200: a breach is a normal answer ({@code allowed: false}), not an error. */
    @PostMapping("/check")
    public RiskCheckResponse check(@Valid @RequestBody RiskCheckRequest request, Authentication authentication) {
        return checks.check(authentication.getName(), request);
    }
}
