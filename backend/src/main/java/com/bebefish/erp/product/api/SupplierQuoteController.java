package com.bebefish.erp.product.api;

import com.bebefish.erp.common.api.ApiResponse;
import com.bebefish.erp.product.application.SupplierQuoteService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/skus/{skuId}/supplier-quotes")
public class SupplierQuoteController {
    private final SupplierQuoteService service;

    public SupplierQuoteController(SupplierQuoteService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('product:view')")
    public ApiResponse<List<SupplierQuoteResponse>> list(@PathVariable long skuId) {
        return ApiResponse.success(service.listQuotes(skuId).stream().map(SupplierQuoteResponse::from).toList());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('product:edit')")
    public ApiResponse<SupplierQuoteResponse> create(
            @PathVariable long skuId,
            @Valid @RequestBody SaveSupplierQuoteRequest request
    ) {
        return ApiResponse.success(SupplierQuoteResponse.from(service.saveQuote(skuId, request.toCommand())));
    }

    @PutMapping("/{quoteId}")
    @PreAuthorize("hasAuthority('product:edit')")
    public ApiResponse<SupplierQuoteResponse> update(
            @PathVariable long skuId,
            @PathVariable long quoteId,
            @Valid @RequestBody SaveSupplierQuoteRequest request
    ) {
        return ApiResponse.success(SupplierQuoteResponse.from(
                service.updateQuote(skuId, quoteId, request.toCommand(), request.syncStandardCost())
        ));
    }

    @PostMapping("/{quoteId}/default")
    @PreAuthorize("hasAuthority('product:edit')")
    public ApiResponse<SupplierQuoteResponse> setDefault(
            @PathVariable long skuId,
            @PathVariable long quoteId,
            @RequestBody(required = false) SetDefaultSupplierQuoteRequest request
    ) {
        var syncStandardCost = request != null && request.syncStandardCost();
        return ApiResponse.success(SupplierQuoteResponse.from(
                service.setDefaultQuote(skuId, quoteId, syncStandardCost)
        ));
    }
}
