package com.bebefish.erp.inventory.api;

import com.bebefish.erp.common.api.ApiResponse;
import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.common.api.PageResponse;
import com.bebefish.erp.inventory.application.InventoryBalanceView;
import com.bebefish.erp.inventory.application.InventoryLedgerView;
import com.bebefish.erp.inventory.application.InventoryQueryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
public class InventoryQueryController {
    private final InventoryQueryService service;

    public InventoryQueryController(InventoryQueryService service) {
        this.service = service;
    }

    @GetMapping("/balances")
    @PreAuthorize("hasAuthority('inventory:view')")
    public ApiResponse<PageResponse<InventoryBalanceView>> balances(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String keyword
    ) {
        var pageable = pageable(page, size, Sort.by("warehouseId").ascending().and(Sort.by("skuId").ascending()));
        return ApiResponse.success(PageResponse.from(service.listBalances(warehouseId, keyword, pageable)));
    }

    @GetMapping("/ledger")
    @PreAuthorize("hasAuthority('inventory:view')")
    public ApiResponse<PageResponse<InventoryLedgerView>> ledger(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long skuId,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String sourceType
    ) {
        var pageable = pageable(page, size, Sort.by("occurredAt").descending().and(Sort.by("id").descending()));
        return ApiResponse.success(PageResponse.from(
                service.listLedger(warehouseId, skuId, direction, sourceType, pageable)
        ));
    }

    private PageRequest pageable(int page, int size, Sort sort) {
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "分页参数无效");
        }
        return PageRequest.of(page - 1, size, sort);
    }
}
