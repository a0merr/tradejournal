package com.amerritt.tradejournal.repository;

import com.amerritt.tradejournal.model.Fill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface FillRepository extends JpaRepository<Fill, Long> {

    List<Fill> findByOrderIdOrderByFilledAtAsc(Long orderId);

    @Query("SELECT COUNT(f) FROM Fill f WHERE f.order.account.id = :accountId")
    long countByAccountId(@Param("accountId") Long accountId);

    @Query("SELECT COALESCE(SUM(f.fee), 0) FROM Fill f WHERE f.order.account.id = :accountId")
    BigDecimal sumFeesByAccountId(@Param("accountId") Long accountId);

    @Query("SELECT COALESCE(SUM(f.quantity * f.price), 0) FROM Fill f WHERE f.order.account.id = :accountId")
    BigDecimal sumGrossNotionalByAccountId(@Param("accountId") Long accountId);
}
