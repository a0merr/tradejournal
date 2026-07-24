package com.amerritt.tradejournal.service;

import com.amerritt.tradejournal.dto.PerformanceResponse;
import com.amerritt.tradejournal.model.Account;
import com.amerritt.tradejournal.repository.FillRepository;
import com.amerritt.tradejournal.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class PerformanceService {

    private final AccountService accountService;
    private final AccountPnlReader pnlReader;
    private final OrderRepository orderRepository;
    private final FillRepository fillRepository;

    public PerformanceService(AccountService accountService, AccountPnlReader pnlReader,
                              OrderRepository orderRepository, FillRepository fillRepository) {
        this.accountService = accountService;
        this.pnlReader = pnlReader;
        this.orderRepository = orderRepository;
        this.fillRepository = fillRepository;
    }

    @Transactional(readOnly = true)
    public PerformanceResponse summarize(Long accountId, Long userId) {
        Account account = accountService.requireOwned(accountId, userId);
        Long id = account.getId();

        long totalOrders = orderRepository.countByAccountId(id);
        long totalFills = fillRepository.countByAccountId(id);
        BigDecimal totalFees = fillRepository.sumFeesByAccountId(id);
        BigDecimal grossNotional = fillRepository.sumGrossNotionalByAccountId(id);

        // One FIFO replay produces realized PnL, win rate, and what is still open — so exposure
        // is priced at the same cost basis the profits were realized against.
        PnlCalculator.Result pnl = pnlReader.compute(id);

        BigDecimal openExposure = BigDecimal.ZERO;
        for (PnlCalculator.OpenPosition open : pnl.openByInstrument().values()) {
            openExposure = openExposure.add(
                    open.netQuantity().abs().multiply(open.avgEntryPrice()));
        }

        return new PerformanceResponse(
                id, totalOrders, totalFills, pnl.openByInstrument().size(), pnl.closedTrades(),
                totalFees, grossNotional, openExposure, pnl.realizedPnl(), pnl.winRate());
    }
}
