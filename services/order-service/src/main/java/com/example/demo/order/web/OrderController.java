package com.example.demo.order.web;

import java.util.List;
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
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.order.service.OrderService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // {@code authentication.getName()} is the JWT subject (the authenticated user). The order owner
    // is always taken from here — never from the request body — so you can only act as yourself.
    @PostMapping
    public ResponseEntity<OrderResponse> place(@Valid @RequestBody PlaceOrderRequest request,
                                               Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.place(request, authentication.getName()));
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id, Authentication authentication) {
        return orderService.get(id, authentication.getName());
    }

    @DeleteMapping("/{id}")
    public OrderResponse cancel(@PathVariable UUID id, Authentication authentication) {
        return orderService.cancel(id, authentication.getName());
    }

    @GetMapping("/history")
    public List<OrderResponse> history(Authentication authentication) {
        return orderService.history(authentication.getName());
    }
}
