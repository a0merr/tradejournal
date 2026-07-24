package com.amerritt.tradejournal.service;

import com.amerritt.tradejournal.dto.PositionResponse;
import com.amerritt.tradejournal.model.Position;
import com.amerritt.tradejournal.repository.PositionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PositionService {

    private final PositionRepository positionRepository;
    private final AccountService accountService;
    private final AccountPnlReader pnlReader;

    public PositionService(PositionRepository positionRepository, AccountService accountService,
                           AccountPnlReader pnlReader) {
        this.positionRepository = positionRepository;
        this.accountService = accountService;
        this.pnlReader = pnlReader;
    }

    /**
     * Positions across every account the user owns.
     *
     * <p>The view supplies the aggregates it can compute in SQL; the FIFO cost basis of what is
     * still open comes from replaying each account's fills, since lot-matching is not something
     * a {@code GROUP BY} can express. One replay per account, not per position.
     */
    @Transactional(readOnly = true)
    public List<PositionResponse> listForUser(Long userId) {
        List<Long> accountIds = accountService.accountIdsForUser(userId);
        if (accountIds.isEmpty()) {
            return List.of();
        }

        List<PositionResponse> responses = new ArrayList<>();
        for (Long accountId : accountIds) {
            Map<Long, PnlCalculator.OpenPosition> open = pnlReader.compute(accountId).openByInstrument();
            for (Position p : positionRepository.findByAccountId(accountId)) {
                PnlCalculator.OpenPosition lot = open.get(p.getInstrumentId());
                // Absent means the instrument has been traded flat: there is no basis to quote.
                responses.add(PositionResponse.from(p, lot == null ? null : lot.avgEntryPrice()));
            }
        }
        return responses;
    }
}
