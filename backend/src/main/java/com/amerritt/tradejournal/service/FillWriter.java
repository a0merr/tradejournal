package com.amerritt.tradejournal.service;

import com.amerritt.tradejournal.dto.FillRequest;
import com.amerritt.tradejournal.dto.FillResponse;
import com.amerritt.tradejournal.model.Account;
import com.amerritt.tradejournal.model.Fill;
import com.amerritt.tradejournal.model.Instrument;
import com.amerritt.tradejournal.model.OrderEntity;
import com.amerritt.tradejournal.model.OrderStatus;
import com.amerritt.tradejournal.repository.FillRepository;
import com.amerritt.tradejournal.repository.InstrumentRepository;
import com.amerritt.tradejournal.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * The transactional half of fill ingestion.
 *
 * <p>Split from {@link FillService} for a reason that is easy to get wrong: a constraint
 * violation poisons the persistence context, so the recovery read cannot happen in the
 * transaction that hit the constraint. Keeping the write and the recovery read as separate
 * beans puts a real transaction boundary between them.
 */
@Service
public class FillWriter {

    private final AccountService accountService;
    private final InstrumentRepository instrumentRepository;
    private final OrderRepository orderRepository;
    private final FillRepository fillRepository;
    private final Clock clock;

    public FillWriter(AccountService accountService, InstrumentRepository instrumentRepository,
                      OrderRepository orderRepository, FillRepository fillRepository, Clock clock) {
        this.accountService = accountService;
        this.instrumentRepository = instrumentRepository;
        this.orderRepository = orderRepository;
        this.fillRepository = fillRepository;
        this.clock = clock;
    }

    /** The fill already recorded for this broker execution, if any. Ownership is enforced first. */
    @Transactional(readOnly = true)
    public Optional<FillResponse> findExisting(Long accountId, Long userId, String externalId) {
        Account account = accountService.requireOwned(accountId, userId);
        return orderRepository.findByAccountIdAndExternalId(account.getId(), externalId)
                .flatMap(order -> fillRepository.findByOrderIdOrderByFilledAtAsc(order.getId())
                        .stream()
                        .findFirst())
                .map(FillResponse::from);
    }

    @Transactional
    public FillResponse insert(FillRequest req, Long userId, String externalId) {
        Account account = accountService.requireOwned(req.accountId(), userId);
        Instrument instrument = resolveInstrument(req);

        OrderEntity order = orderRepository.save(new OrderEntity(
                account, instrument, req.side(), req.type(), req.quantity(),
                OrderStatus.FILLED, externalId));

        BigDecimal fee = req.fee() != null ? req.fee() : BigDecimal.ZERO;
        OffsetDateTime filledAt = req.filledAt() != null
                ? req.filledAt()
                : OffsetDateTime.now(clock);

        Fill fill = fillRepository.save(new Fill(order, req.quantity(), req.price(), fee, filledAt));
        return FillResponse.from(fill);
    }

    /**
     * Reference data is created on first sight. A concurrent ingest of the same new symbol
     * loses on the unique {@code (symbol, exchange)} constraint; that violation is left to
     * propagate so {@link FillService} can retry the whole insert in a clean transaction.
     */
    private Instrument resolveInstrument(FillRequest req) {
        return instrumentRepository.findBySymbolAndExchange(req.symbol(), req.exchange())
                .orElseGet(() -> instrumentRepository.save(
                        new Instrument(req.symbol(), req.exchange(), req.assetClass())));
    }
}
