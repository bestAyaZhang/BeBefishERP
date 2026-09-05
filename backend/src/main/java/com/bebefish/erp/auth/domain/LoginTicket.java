package com.bebefish.erp.auth.domain;

import java.util.List;

public record LoginTicket(long userId, List<String> warnings) {
    public LoginTicket {
        warnings = List.copyOf(warnings);
    }
}
