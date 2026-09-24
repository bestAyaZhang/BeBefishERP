package com.bebefish.erp.shipping.domain;

import java.time.LocalDate;

public record ShipmentQuery(String keyword, String status, LocalDate dateFrom, LocalDate dateTo,
                            String platform, boolean incompleteOnly) {}
