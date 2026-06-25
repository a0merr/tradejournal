package com.amerritt.tradejournal.service;

import com.amerritt.tradejournal.dto.PerformanceResponse;
import com.amerritt.tradejournal.model.Account;
import com.amerritt.tradejournal.model.Position;
import com.amerritt.tradejournal.repository.FillRepository;
import com.amerritt.tradejournal.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class PerformanceService {

    private static final BigDecimal ZERO_QTY = BigDecimal.ZERO;

    private final AccountService accountService;
    private final PositionService positionService;
    private final OrderRepository orderRepository;
    private final FillRepository fillRepository;

    public PerformanceService(AccountService accountService, PositionService positionService,
                              OrderRepository orderRepository, FillRepository fillRepository) {
        this.accountService = accountService;
        this.positionService = positionService;
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

        List<Position> positions = positionService.rawForAccount(id);
        long openPositions = 0;
        BigDecimal openExposure = BigDecimal.ZERO;
        for (Position p : positions) {
            BigDecimal net = p.getNetQuantity() != null ? p.getNetQuantity() : ZERO_QTY;
            if (net.signum() != 0) {
                openPositions++;
                BigDecimal avg = p.getAvgPrice() != null ? p.getAvgPrice() : BigDecimal.ZERO;
                openExposure = openExposure.add(net.abs().multiply(avg));
            }
        }

        // Realized PnL + win rate from FIFO lot-matching over the account's fill stream.
        PnlCalculator.Result pnl = PnlCalculator.compute(fillRepository.findAccountFillsForPnl(id));

        return new PerformanceResponse(
                id, totalOrders, totalFills, openPositions, pnl.closedTrades(),
                totalFees, grossNotional, openExposure, pnl.realizedPnl(), pnl.winRate());
    }
}
