package com.amerritt.tradejournal.service;

import com.amerritt.tradejournal.dto.PositionResponse;
import com.amerritt.tradejournal.model.Position;
import com.amerritt.tradejournal.repository.PositionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PositionService {

    private final PositionRepository positionRepository;
    private final AccountService accountService;

    public PositionService(PositionRepository positionRepository, AccountService accountService) {
        this.positionRepository = positionRepository;
        this.accountService = accountService;
    }

    @Transactional(readOnly = true)
    public List<PositionResponse> listForUser(Long userId) {
        List<Long> accountIds = accountService.accountIdsForUser(userId);
        if (accountIds.isEmpty()) {
            return List.of();
        }
        return positionRepository.findByAccountIdIn(accountIds).stream()
                .map(PositionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Position> rawForAccount(Long accountId) {
        return positionRepository.findByAccountId(accountId);
    }
}
