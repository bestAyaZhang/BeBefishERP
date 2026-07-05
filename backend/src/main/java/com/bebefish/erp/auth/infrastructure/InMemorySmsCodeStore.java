package com.bebefish.erp.auth.infrastructure;

import com.bebefish.erp.auth.application.AuthException;
import com.bebefish.erp.auth.domain.SmsCodeStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InMemorySmsCodeStore implements SmsCodeStore {
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration SEND_COOLDOWN = Duration.ofSeconds(60);

    private final Clock clock;
    private final String devCode;
    private final Map<String, CodeEntry> codes = new ConcurrentHashMap<>();

    public InMemorySmsCodeStore(
            Clock clock,
            @Value("${erp.auth.sms-dev-code:123456}") String devCode
    ) {
        this.clock = clock;
        this.devCode = devCode;
    }

    @Override
    public void issueCode(String mobile) {
        var now = Instant.now(clock);
        var existing = codes.get(mobile);
        if (existing != null && now.isBefore(existing.issuedAt().plus(SEND_COOLDOWN))) {
            throw new AuthException("SMS_SEND_TOO_FREQUENT", "验证码发送过于频繁，请稍后再试");
        }
        codes.put(mobile, new CodeEntry(devCode, now, now.plus(CODE_TTL)));
    }

    @Override
    public boolean verifyAndConsume(String mobile, String smsCode) {
        var now = Instant.now(clock);
        var entry = codes.get(mobile);
        if (entry == null || now.isAfter(entry.expiresAt())) {
            codes.remove(mobile);
            return false;
        }
        if (!entry.code().equals(smsCode)) {
            return false;
        }
        codes.remove(mobile);
        return true;
    }

    private record CodeEntry(String code, Instant issuedAt, Instant expiresAt) {
    }
}
