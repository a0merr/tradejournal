package com.amerritt.tradejournal.dto;

import java.util.List;

/**
 * Outcome of a CSV fill import. Good rows are committed even when others fail;
 * {@code errors} reports each rejected row by its line number in the file.
 *
 * <p>{@code skipped} counts rows whose {@code externalId} was already on file. Re-importing an
 * overlapping statement is therefore a normal thing to do, and the count says how much overlap
 * there was rather than hiding it inside {@code imported}.
 */
public record ImportResult(int imported, int skipped, int failed, List<RowError> errors) {

    public record RowError(long line, String message) {
    }
}
