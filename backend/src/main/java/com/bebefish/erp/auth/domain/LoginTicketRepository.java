package com.bebefish.erp.auth.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LoginTicketRepository {
    void save(String ticketHash, long userId, List<String> warnings, Instant createdAt, Instant expiresAt);

    Optional<LoginTicket> consume(String ticketHash, Instant now);
}
