package com.amerritt.tradejournal.repository;

import com.amerritt.tradejournal.model.OrderEntity;
import com.amerritt.tradejournal.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    List<OrderEntity> findByAccountIdInOrderByCreatedAtDesc(List<Long> accountIds);

    List<OrderEntity> findByAccountIdInAndStatusOrderByCreatedAtDesc(List<Long> accountIds, OrderStatus status);

    Optional<OrderEntity> findByIdAndAccountIdIn(Long id, List<Long> accountIds);

    long countByAccountId(Long accountId);
}
