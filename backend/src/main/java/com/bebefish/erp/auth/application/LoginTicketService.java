package com.bebefish.erp.auth.application;

import com.bebefish.erp.auth.domain.LoginTicket;
import com.bebefish.erp.auth.domain.LoginTicketRepository;
import com.bebefish.erp.auth.infrastructure.Sha256TokenHasher;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginTicketService {
    private final LoginTicketRepository tickets;
    private final Sha256TokenHasher hasher;
    private final Clock clock;
    private final Duration ticketTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    public LoginTicketService(
            LoginTicketRepository tickets,
            Sha256TokenHasher hasher,
            Clock clock,
            @Value("${erp.auth.ticket-ttl:PT60S}") Duration ticketTtl
    ) {
        this.tickets = tickets;
        this.hasher = hasher;
        this.clock = clock;
        this.ticketTtl = ticketTtl;
    }

    public String issue(long userId, List<String> warnings) {
        String ticket = randomToken();
        Instant now = Instant.now(clock);
        tickets.save(hasher.hash(ticket), userId, warnings, now, now.plus(ticketTtl));
        return ticket;
    }

    @Transactional
    public LoginTicket consume(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            throw expired();
        }
        return tickets.consume(hasher.hash(ticket), Instant.now(clock)).orElseThrow(this::expired);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private AuthException expired() {
        return new AuthException("FEISHU_CALLBACK_EXPIRED", "登录结果不存在或已过期，请重新扫码");
    }
}
