package com.amerritt.tradejournal.dto;

import java.util.List;

/**
 * Outcome of a CSV fill import. Good rows are committed even when others fail;
 * {@code errors} reports each rejected row by its line number in the file.
 */
public record ImportResult(int imported, int failed, List<RowError> errors) {

    public record RowError(long line, String message) {
    }
}
