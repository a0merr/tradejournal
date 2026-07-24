package com.amerritt.tradejournal.repository;

import com.amerritt.tradejournal.model.Fill;
import jakarta.persistence.QueryHint;
import org.hibernate.jpa.AvailableHints;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

public interface FillRepository extends JpaRepository<Fill, Long> {

    List<Fill> findByOrderIdOrderByFilledAtAsc(Long orderId);

    @Query("SELECT COUNT(f) FROM Fill f WHERE f.order.account.id = :accountId")
    long countByAccountId(@Param("accountId") Long accountId);

    @Query("SELECT COALESCE(SUM(f.fee), 0) FROM Fill f WHERE f.order.account.id = :accountId")
    BigDecimal sumFeesByAccountId(@Param("accountId") Long accountId);

    @Query("SELECT COALESCE(SUM(f.quantity * f.price), 0) FROM Fill f WHERE f.order.account.id = :accountId")
    BigDecimal sumGrossNotionalByAccountId(@Param("accountId") Long accountId);

    /**
     * An account's fills in execution order, with order + instrument fetched, for PnL.
     *
     * <p>A {@link Stream} rather than a list: a bot fills continuously, so the history is
     * unbounded and materialising it was a heap-sized-per-request problem waiting to happen.
     * The fetch-size hint makes the driver pull rows in batches instead of buffering the whole
     * result set. Must be consumed inside a transaction and closed (try-with-resources).
     */
    @QueryHints(@QueryHint(name = AvailableHints.HINT_FETCH_SIZE, value = "500"))
    @Query("SELECT f FROM Fill f JOIN FETCH f.order o JOIN FETCH o.instrument "
            + "WHERE o.account.id = :accountId ORDER BY f.filledAt ASC, f.id ASC")
    Stream<Fill> streamAccountFillsForPnl(@Param("accountId") Long accountId);
}
