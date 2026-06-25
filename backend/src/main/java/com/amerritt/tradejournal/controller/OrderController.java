package com.amerritt.tradejournal.controller;

import com.amerritt.tradejournal.dto.OrderResponse;
import com.amerritt.tradejournal.model.OrderStatus;
import com.amerritt.tradejournal.security.AuthPrincipal;
import com.amerritt.tradejournal.service.OrderService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public List<OrderResponse> list(@RequestParam(required = false) OrderStatus status,
                                    @AuthenticationPrincipal AuthPrincipal principal) {
        return orderService.listForUser(principal.userId(), status);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id,
                             @AuthenticationPrincipal AuthPrincipal principal) {
        return orderService.getForUser(id, principal.userId());
    }
}
