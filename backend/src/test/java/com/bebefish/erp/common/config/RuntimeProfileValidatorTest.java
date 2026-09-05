package com.bebefish.erp.common.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

class RuntimeProfileValidatorTest {
    @Test
    void rejectsProductionCombinedWithLocalOrTestProfiles() {
        assertThatThrownBy(() -> RuntimeProfileValidator.validate(Set.of("prod", "local")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("prod");
        assertThatThrownBy(() -> RuntimeProfileValidator.validate(Set.of("prod", "test")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("prod");
    }

    @Test
    void acceptsIsolatedRuntimeProfiles() {
        assertThatCode(() -> RuntimeProfileValidator.validate(Set.of("prod"))).doesNotThrowAnyException();
        assertThatCode(() -> RuntimeProfileValidator.validate(Set.of("local"))).doesNotThrowAnyException();
        assertThatCode(() -> RuntimeProfileValidator.validate(Set.of("test"))).doesNotThrowAnyException();
    }
}
