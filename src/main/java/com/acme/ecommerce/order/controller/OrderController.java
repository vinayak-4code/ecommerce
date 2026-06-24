package com.acme.ecommerce.order.controller;

import com.acme.ecommerce.common.security.CurrentUser;
import com.acme.ecommerce.order.dto.CreateOrderRequest;
import com.acme.ecommerce.order.dto.OrderResponse;
import com.acme.ecommerce.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse placeOrder(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.placeOrder(CurrentUser.require().userId(), request);
    }

    @GetMapping("/{orderId}")
    public OrderResponse get(@PathVariable UUID orderId) {
        return orderService.get(CurrentUser.require().userId(), orderId);
    }

    @GetMapping
    public List<OrderResponse> list() {
        return orderService.list(CurrentUser.require().userId());
    }
}
