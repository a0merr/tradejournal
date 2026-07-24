package com.amerritt.tradejournal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enforces the no-floating-point rule structurally rather than by review.
 *
 * <p>{@code double} and {@code float} cannot represent 0.1, so accumulating them across fills
 * drifts: a P&L that is off by a cent is a wrong number, not a rounding preference. Code review
 * catches the first such field and misses the fifth, so the rule is asserted here — a new
 * {@code double price} fails the build with the reason attached.
 */
class MonetaryPrecisionTest {

    private static final String ROOT = "com.amerritt.tradejournal";
    private static final Set<Class<?>> BANNED = Set.of(
            double.class, float.class, Double.class, Float.class);

    @Test
    void noEntityOrDtoUsesBinaryFloatingPoint() {
        List<String> offenders = new ArrayList<>();
        for (Class<?> type : scan(ROOT + ".model", ROOT + ".dto")) {
            for (Field field : type.getDeclaredFields()) {
                if (!field.isSynthetic() && BANNED.contains(field.getType())) {
                    offenders.add(type.getSimpleName() + "." + field.getName()
                            + " is " + field.getType().getSimpleName());
                }
            }
        }
        assertThat(offenders)
                .as("money and quantity must be BigDecimal, never binary floating point")
                .isEmpty();
    }

    /**
     * A {@code BigDecimal} without an explicit precision/scale is mapped by Hibernate at its
     * default (19,2) — which silently truncates the eighth decimal place that crypto quantities
     * need. Read-only view mappings are exempt: their types come from the view, not from DDL
     * this annotation generates.
     */
    @Test
    void mappedDecimalColumnsDeclarePrecisionAndScale() {
        List<String> offenders = new ArrayList<>();
        for (Class<?> type : scan(ROOT + ".model")) {
            if (!type.isAnnotationPresent(Entity.class) || type.isAnnotationPresent(Immutable.class)) {
                continue;
            }
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() != BigDecimal.class) {
                    continue;
                }
                Column column = field.getAnnotation(Column.class);
                if (column == null || column.precision() == 0) {
                    offenders.add(type.getSimpleName() + "." + field.getName());
                }
            }
        }
        assertThat(offenders)
                .as("@Column(precision, scale) must be explicit on persisted BigDecimal fields")
                .isEmpty();
    }

    /** Sanity check on the scanner itself, so an empty scan cannot pass the tests above. */
    @Test
    void scannerFindsTheModelAndDtoTypes() {
        assertThat(scan(ROOT + ".model")).hasSizeGreaterThan(5);
        assertThat(scan(ROOT + ".dto")).hasSizeGreaterThan(5);
    }

    private static List<Class<?>> scan(String... packages) {
        ClassPathScanningCandidateComponentProvider provider =
                new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter((metadataReader, factory) -> true);

        List<Class<?>> classes = new ArrayList<>();
        for (String pkg : packages) {
            for (BeanDefinition definition : provider.findCandidateComponents(pkg)) {
                try {
                    Class<?> type = Class.forName(definition.getBeanClassName());
                    if (!type.isInterface() && !Modifier.isAbstract(type.getModifiers())) {
                        classes.add(type);
                    }
                } catch (ClassNotFoundException ex) {
                    throw new IllegalStateException("Scanned class is not loadable", ex);
                }
            }
        }
        return classes;
    }
}
