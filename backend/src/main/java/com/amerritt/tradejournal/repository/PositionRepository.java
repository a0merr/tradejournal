package com.amerritt.tradejournal.repository;

import com.amerritt.tradejournal.model.Position;
import com.amerritt.tradejournal.model.PositionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PositionRepository extends JpaRepository<Position, PositionId> {

    List<Position> findByAccountIdIn(List<Long> accountIds);

    List<Position> findByAccountId(Long accountId);
}
