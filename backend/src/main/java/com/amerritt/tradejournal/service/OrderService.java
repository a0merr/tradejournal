package com.amerritt.tradejournal.service;

import com.amerritt.tradejournal.dto.FillResponse;
import com.amerritt.tradejournal.dto.OrderResponse;
import com.amerritt.tradejournal.exception.ResourceNotFoundException;
import com.amerritt.tradejournal.model.OrderEntity;
import com.amerritt.tradejournal.model.OrderStatus;
import com.amerritt.tradejournal.repository.FillRepository;
import com.amerritt.tradejournal.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final FillRepository fillRepository;
    private final AccountService accountService;

    public OrderService(OrderRepository orderRepository, FillRepository fillRepository,
                        AccountService accountService) {
        this.orderRepository = orderRepository;
        this.fillRepository = fillRepository;
        this.accountService = accountService;
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listForUser(Long userId, OrderStatus status) {
        List<Long> accountIds = accountService.accountIdsForUser(userId);
        if (accountIds.isEmpty()) {
            return List.of();
        }
        List<OrderEntity> orders = (status == null)
                ? orderRepository.findByAccountIdInOrderByCreatedAtDesc(accountIds)
                : orderRepository.findByAccountIdInAndStatusOrderByCreatedAtDesc(accountIds, status);
        // List view omits per-fill detail to keep payloads small.
        return orders.stream()
                .map(o -> OrderResponse.from(o, List.of()))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getForUser(Long orderId, Long userId) {
        List<Long> accountIds = accountService.accountIdsForUser(userId);
        OrderEntity order = accountIds.isEmpty()
                ? null
                : orderRepository.findByIdAndAccountIdIn(orderId, accountIds).orElse(null);
        if (order == null) {
            throw new ResourceNotFoundException("Order " + orderId + " not found for current user");
        }
        List<FillResponse> fills = fillRepository.findByOrderIdOrderByFilledAtAsc(orderId).stream()
                .map(FillResponse::from)
                .toList();
        return OrderResponse.from(order, fills);
    }
}
