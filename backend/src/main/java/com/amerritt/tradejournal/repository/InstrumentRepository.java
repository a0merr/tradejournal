package com.amerritt.tradejournal.repository;

import com.amerritt.tradejournal.model.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InstrumentRepository extends JpaRepository<Instrument, Long> {

    Optional<Instrument> findBySymbolAndExchange(String symbol, String exchange);
}
