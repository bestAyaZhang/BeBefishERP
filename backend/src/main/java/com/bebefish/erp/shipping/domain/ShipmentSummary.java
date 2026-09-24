package com.bebefish.erp.shipping.domain;

public record ShipmentSummary(
        long todayCount,
        long unfinishedCount,
        long completedCount,
        long outOfStockCount,
        long partiallyShippedCount
) {}
