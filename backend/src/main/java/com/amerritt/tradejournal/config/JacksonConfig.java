package com.amerritt.tradejournal.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * The last mile of the no-floating-point rule.
 *
 * <p>Money and quantities are {@code NUMERIC} in PostgreSQL and {@link BigDecimal} in Java, but
 * a JSON <em>number</em> undoes both: JSON has no decimal type, so every mainstream client
 * (browsers included) parses {@code 0.1} into an IEEE-754 double. Serialising every
 * {@code BigDecimal} as a JSON string keeps the exact decimal all the way to the consumer, which
 * can then decode it with its own arbitrary-precision type.
 *
 * <p>Only {@code BigDecimal} is affected — identifiers and counts stay JSON numbers. Inbound
 * parsing is unchanged: Jackson already accepts both {@code "1.5"} and {@code 1.5} for a
 * {@code BigDecimal} field, so existing bot clients keep working.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer bigDecimalAsString() {
        return builder -> builder.serializerByType(BigDecimal.class, new PlainStringBigDecimalSerializer());
    }

    /** Writes the plain (never scientific) representation, preserving trailing-zero scale. */
    private static final class PlainStringBigDecimalSerializer extends JsonSerializer<BigDecimal> {

        @Override
        public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeString(value.toPlainString());
        }
    }
}
