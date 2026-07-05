package com.bebefish.erp.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bebefish.erp.auth.application.AuthException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class InMemorySmsCodeStoreTest {
    @Test
    void issuedCodeCanBeUsedOnlyOnce() {
        var smsCodes = new InMemorySmsCodeStore(fixedClock("2026-07-05T10:00:00Z"), "123456");

        smsCodes.issueCode("13800138000");

        assertThat(smsCodes.verifyAndConsume("13800138000", "123456")).isTrue();
        assertThat(smsCodes.verifyAndConsume("13800138000", "123456")).isFalse();
    }

    @Test
    void sendingTwiceWithinCooldownIsRejected() {
        var smsCodes = new InMemorySmsCodeStore(fixedClock("2026-07-05T10:00:00Z"), "123456");
        smsCodes.issueCode("13800138000");

        assertThatThrownBy(() -> smsCodes.issueCode("13800138000"))
                .isInstanceOf(AuthException.class)
                .hasMessage("验证码发送过于频繁，请稍后再试");
    }

    private Clock fixedClock(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneId.of("UTC"));
    }
}
