package com.amerritt.tradejournal.service;

import com.amerritt.tradejournal.dto.FillRequest;
import com.amerritt.tradejournal.dto.FillResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Ingests fills from the bot, idempotently when the caller supplies a broker execution id.
 *
 * <p>Deliberately not {@code @Transactional}: it coordinates across transaction boundaries.
 * Each attempt runs in its own transaction via {@link FillWriter}, which is what lets a
 * constraint violation be recovered from rather than merely reported.
 */
@Service
public class FillService {

    private final FillWriter writer;

    public FillService(FillWriter writer) {
        this.writer = writer;
    }

    /**
     * @param duplicate true when the execution was already on file and nothing was written —
     *                  the caller should answer 200, not 201
     */
    public record Ingested(FillResponse fill, boolean duplicate) {
    }

    public Ingested ingest(FillRequest req, Long userId) {
        String externalId = trimToNull(req.externalId());

        if (externalId != null) {
            Optional<FillResponse> existing =
                    writer.findExisting(req.accountId(), userId, externalId);
            if (existing.isPresent()) {
                return new Ingested(existing.get(), true);
            }
        }

        try {
            return new Ingested(writer.insert(req, userId, externalId), false);
        } catch (DataIntegrityViolationException race) {
            return recover(req, userId, externalId, race);
        }
    }

    /**
     * Two constraints can be lost to a concurrent request: the idempotency key, meaning the
     * other request already recorded this execution, and the unique instrument, meaning both
     * requests were first to see a new symbol.
     */
    private Ingested recover(FillRequest req, Long userId, String externalId,
                             DataIntegrityViolationException race) {
        if (externalId != null) {
            Optional<FillResponse> existing =
                    writer.findExisting(req.accountId(), userId, externalId);
            if (existing.isPresent()) {
                return new Ingested(existing.get(), true);
            }
        }
        // Not a duplicate execution, so it was the instrument race: the winner's row is now
        // committed and visible, and a second attempt reads it instead of inserting.
        try {
            return new Ingested(writer.insert(req, userId, externalId), false);
        } catch (DataIntegrityViolationException stillFailing) {
            stillFailing.addSuppressed(race);
            throw stillFailing;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
