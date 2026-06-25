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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Service
public class FillService {

    private final AccountService accountService;
    private final InstrumentRepository instrumentRepository;
    private final OrderRepository orderRepository;
    private final FillRepository fillRepository;

    public FillService(AccountService accountService, InstrumentRepository instrumentRepository,
                       OrderRepository orderRepository, FillRepository fillRepository) {
        this.accountService = accountService;
        this.instrumentRepository = instrumentRepository;
        this.orderRepository = orderRepository;
        this.fillRepository = fillRepository;
    }

    /**
     * Ingests one fill from the bot. The payload is self-describing, so the service
     * resolves (or creates) the instrument, records a single FILLED order for this
     * execution, and stores the fill against it.
     */
    @Transactional
    public FillResponse ingest(FillRequest req, Long userId) {
        Account account = accountService.requireOwned(req.accountId(), userId);
        Instrument instrument = resolveInstrument(req);

        OrderEntity order = orderRepository.save(new OrderEntity(
                account, instrument, req.side(), req.type(), req.quantity(), OrderStatus.FILLED));

        BigDecimal fee = req.fee() != null ? req.fee() : BigDecimal.ZERO;
        OffsetDateTime filledAt = req.filledAt() != null ? req.filledAt() : OffsetDateTime.now();

        Fill fill = fillRepository.save(new Fill(order, req.quantity(), req.price(), fee, filledAt));
        return FillResponse.from(fill);
    }

    private Instrument resolveInstrument(FillRequest req) {
        return instrumentRepository.findBySymbolAndExchange(req.symbol(), req.exchange())
                .orElseGet(() -> {
                    try {
                        return instrumentRepository.save(
                                new Instrument(req.symbol(), req.exchange(), req.assetClass()));
                    } catch (DataIntegrityViolationException race) {
                        // Lost a race on the unique (symbol, exchange) constraint; re-read the winner.
                        return instrumentRepository.findBySymbolAndExchange(req.symbol(), req.exchange())
                                .orElseThrow(() -> race);
                    }
                });
    }
}
