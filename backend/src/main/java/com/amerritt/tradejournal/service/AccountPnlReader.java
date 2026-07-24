package com.amerritt.tradejournal.service;

import com.amerritt.tradejournal.model.Fill;
import com.amerritt.tradejournal.repository.FillRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Iterator;
import java.util.stream.Stream;

/**
 * Replays an account's fill stream through {@link PnlCalculator}.
 *
 * <p>Single place where the replay happens, so realized PnL and the open-position cost basis
 * are always produced by the same accounting method — reporting FIFO profits next to an
 * average-cost basis would be two different sets of books.
 *
 * <p>Replaying the whole history on every request is deliberate at this size and is the honest
 * version: no denormalised total can drift from the fills. The next step, once fill counts
 * justify it, is a per-(account, instrument) snapshot advanced at ingest with this replay kept
 * as the reconciliation path.
 */
@Component
public class AccountPnlReader {

    /**
     * How many fills to hold before clearing the persistence context. Streaming alone does not
     * bound memory — Hibernate keeps every entity it hands out — so the context is emptied as
     * the scan advances. Safe here because nothing is mutated and each fill is fully consumed
     * before the next is pulled.
     */
    private static final int DETACH_BATCH = 500;

    private final FillRepository fillRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public AccountPnlReader(FillRepository fillRepository) {
        this.fillRepository = fillRepository;
    }

    @Transactional(readOnly = true)
    public PnlCalculator.Result compute(Long accountId) {
        try (Stream<Fill> fills = fillRepository.streamAccountFillsForPnl(accountId)) {
            return PnlCalculator.compute(new DetachingIterator(fills.iterator()));
        }
    }

    private final class DetachingIterator implements Iterator<Fill> {

        private final Iterator<Fill> delegate;
        private long seen;

        private DetachingIterator(Iterator<Fill> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public Fill next() {
            if (seen > 0 && seen % DETACH_BATCH == 0) {
                entityManager.clear();
            }
            seen++;
            return delegate.next();
        }
    }
}
