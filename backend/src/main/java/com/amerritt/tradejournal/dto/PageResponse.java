package com.amerritt.tradejournal.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * A page of results.
 *
 * <p>A hand-rolled shape rather than Spring's {@code Page}: the serialized form of {@code Page}
 * is an implementation detail Spring explicitly warns against exposing, and it would change
 * under the client's feet on an upgrade. This is the API's own contract.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }
}
