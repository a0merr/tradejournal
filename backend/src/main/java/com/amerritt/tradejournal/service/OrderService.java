package com.amerritt.tradejournal.service;

import com.amerritt.tradejournal.dto.FillResponse;
import com.amerritt.tradejournal.dto.OrderResponse;
import com.amerritt.tradejournal.dto.PageResponse;
import com.amerritt.tradejournal.exception.ResourceNotFoundException;
import com.amerritt.tradejournal.model.OrderEntity;
import com.amerritt.tradejournal.model.OrderStatus;
import com.amerritt.tradejournal.repository.FillRepository;
import com.amerritt.tradejournal.repository.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderService {

    /** Hard ceiling on page size: the client does not get to ask for the whole table. */
    private static final int MAX_PAGE_SIZE = 200;

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
    public PageResponse<OrderResponse> listForUser(Long userId, OrderStatus status, int page, int size) {
        List<Long> accountIds = accountService.accountIdsForUser(userId);
        if (accountIds.isEmpty()) {
            return new PageResponse<>(List.of(), page, size, 0, 0, true);
        }
        // Tie-broken by id: created_at alone is not unique, and a non-deterministic sort makes
        // rows repeat or vanish between pages.
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size));
        Page<OrderEntity> orders = (status == null)
                ? orderRepository.findByAccountIdInOrderByCreatedAtDescIdDesc(accountIds, pageable)
                : orderRepository.findByAccountIdInAndStatusOrderByCreatedAtDescIdDesc(
                        accountIds, status, pageable);
        // List view omits per-fill detail to keep payloads small.
        return PageResponse.of(orders, o -> OrderResponse.from(o, List.of()));
    }

    private static int clampSize(int size) {
        if (size < 1) {
            return 1;
        }
        return Math.min(size, MAX_PAGE_SIZE);
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
