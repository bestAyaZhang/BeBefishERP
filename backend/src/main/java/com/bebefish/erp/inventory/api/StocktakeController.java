package com.bebefish.erp.inventory.api;

import com.bebefish.erp.common.api.ApiResponse;
import com.bebefish.erp.common.security.ErpPrincipal;
import com.bebefish.erp.inventory.application.StocktakeService;
import com.bebefish.erp.inventory.application.StocktakeViews.Details;
import com.bebefish.erp.inventory.application.StocktakeViews.Item;
import com.bebefish.erp.inventory.application.StocktakeViews.ListResult;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory/stocktakes")
public class StocktakeController {
    private final StocktakeService service;

    public StocktakeController(StocktakeService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('inventory:view')")
    public ApiResponse<ListResult> list(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(service.list(warehouseId, status, keyword));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('inventory:view')")
    public ApiResponse<Details> get(@PathVariable long id) {
        return ApiResponse.success(visibleDetails(service.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('inventory:view') and hasAuthority('inventory:edit') and hasAuthority('warehouse:view')")
    public ApiResponse<Details> create(
            @Valid @RequestBody CreateStocktakeRequest request,
            @AuthenticationPrincipal ErpPrincipal principal
    ) {
        return ApiResponse.success(visibleDetails(service.create(
                request.warehouseId(), assigneeName(principal), request.blindCount(), request.palletIds(),
                principal.operatorIdentifier()
        )));
    }

    private String assigneeName(ErpPrincipal principal) {
        return principal.displayName() == null || principal.displayName().isBlank()
                ? principal.operatorIdentifier()
                : principal.displayName().trim();
    }

    @PutMapping("/{id}/draft")
    @PreAuthorize("hasAuthority('inventory:edit')")
    public ApiResponse<Details> saveDraft(
            @PathVariable long id,
            @Valid @RequestBody SaveStocktakeCountsRequest request
    ) {
        return ApiResponse.success(visibleDetails(service.saveDraft(id, request.toCounts())));
    }

    @PostMapping("/{id}/submit-initial")
    @PreAuthorize("hasAuthority('inventory:edit')")
    public ApiResponse<Details> submitInitial(
            @PathVariable long id,
            @Valid @RequestBody SaveStocktakeCountsRequest request
    ) {
        return ApiResponse.success(visibleDetails(service.submitInitial(id, request.toCounts())));
    }

    @PostMapping("/{id}/submit-recount")
    @PreAuthorize("hasAuthority('inventory:edit')")
    public ApiResponse<Details> submitRecount(
            @PathVariable long id,
            @Valid @RequestBody SaveStocktakeCountsRequest request
    ) {
        return ApiResponse.success(visibleDetails(service.submitRecount(id, request.toCounts())));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('inventory:approve')")
    public ApiResponse<Details> approve(
            @PathVariable long id,
            @AuthenticationPrincipal ErpPrincipal principal
    ) {
        return ApiResponse.success(visibleDetails(service.approve(id, principal.operatorIdentifier())));
    }

    private Details visibleDetails(Details details) {
        if (!details.blindCount() || !("not_started".equals(details.status())
                || "in_progress".equals(details.status()))) {
            return details;
        }
        var hiddenItems = details.items().stream().map(item -> new Item(
                item.id(), item.zoneName(), item.palletId(), item.palletLabel(), item.skuId(),
                item.skuCode(), item.productName(), item.skuName(), item.specification(),
                item.unitsPerCase(), null, item.firstCountQuantity(), item.recountQuantity(),
                null, item.status()
        )).toList();
        return new Details(details.id(), details.taskNo(), details.warehouseId(), details.warehouseName(),
                details.scopeLabel(), details.assigneeName(), details.countedItems(), details.totalItems(),
                details.differenceItems(), details.status(), details.createdAt(), details.blindCount(), hiddenItems);
    }
}
