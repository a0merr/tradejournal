package com.amerritt.tradejournal.repository;

import com.amerritt.tradejournal.model.OrderEntity;
import com.amerritt.tradejournal.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    Page<OrderEntity> findByAccountIdInOrderByCreatedAtDescIdDesc(
            List<Long> accountIds, Pageable pageable);

    Page<OrderEntity> findByAccountIdInAndStatusOrderByCreatedAtDescIdDesc(
            List<Long> accountIds, OrderStatus status, Pageable pageable);

    Optional<OrderEntity> findByIdAndAccountIdIn(Long id, List<Long> accountIds);

    long countByAccountId(Long accountId);

    /** Idempotency lookup: has this broker execution already been ingested for this account? */
    Optional<OrderEntity> findByAccountIdAndExternalId(Long accountId, String externalId);
}
