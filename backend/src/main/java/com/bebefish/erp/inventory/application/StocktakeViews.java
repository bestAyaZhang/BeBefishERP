package com.bebefish.erp.inventory.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class StocktakeViews {
    private StocktakeViews() {
    }

    public record Summary(
            int inProgress,
            int awaitingRecount,
            int awaitingApproval,
            int completedThisMonth,
            int discrepancyItems,
            BigDecimal accuracyRate
    ) {
    }

    public record Task(
            long id,
            String taskNo,
            long warehouseId,
            String warehouseName,
            String scopeLabel,
            String assigneeName,
            int countedItems,
            int totalItems,
            int differenceItems,
            String status,
            LocalDateTime createdAt
    ) {
    }

    public record Item(
            long id,
            String zoneName,
            String palletId,
            String palletLabel,
            long skuId,
            String skuCode,
            String productName,
            String skuName,
            String specification,
            Integer unitsPerCase,
            BigDecimal bookQuantity,
            BigDecimal firstCountQuantity,
            BigDecimal recountQuantity,
            BigDecimal difference,
            String status
    ) {
    }

    public record Details(
            long id,
            String taskNo,
            long warehouseId,
            String warehouseName,
            String scopeLabel,
            String assigneeName,
            int countedItems,
            int totalItems,
            int differenceItems,
            String status,
            LocalDateTime createdAt,
            boolean blindCount,
            List<Item> items
    ) {
        public Details {
            items = List.copyOf(items);
        }
    }

    public record ListResult(Summary summary, List<Task> tasks) {
        public ListResult {
            tasks = List.copyOf(tasks);
        }
    }

    public record Count(long itemId, BigDecimal quantity) {
    }
}
