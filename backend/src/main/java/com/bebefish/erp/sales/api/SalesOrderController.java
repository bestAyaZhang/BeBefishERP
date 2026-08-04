package com.bebefish.erp.sales.api;

import com.bebefish.erp.common.api.ApiResponse;
import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.common.api.PageResponse;
import com.bebefish.erp.common.security.ErpPrincipal;
import com.bebefish.erp.sales.application.SalesDraftService;
import com.bebefish.erp.sales.domain.SalesOrderSearchCriteria;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sales-orders")
public class SalesOrderController {
    private final SalesDraftService service;

    public SalesOrderController(SalesDraftService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sales:view')")
    public ApiResponse<PageResponse<SalesOrderResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo
    ) {
        validatePage(page, size);
        var result = service.list(
                new SalesOrderSearchCriteria(keyword, status, dateFrom, dateTo),
                PageRequest.of(page - 1, size, Sort.by(Sort.Order.desc("salesDate"), Sort.Order.desc("id")))
        ).map(SalesOrderResponse::from);
        return ApiResponse.success(PageResponse.from(result));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sales:view')")
    public ApiResponse<SalesOrderResponse> get(@PathVariable long id) {
        return ApiResponse.success(SalesOrderResponse.from(service.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('sales:create')")
    public ApiResponse<SalesOrderResponse> create(
            @Valid @RequestBody SaveSalesOrderRequest request,
            @AuthenticationPrincipal ErpPrincipal principal
    ) {
        return ApiResponse.success(SalesOrderResponse.from(service.create(request.toCommand(), principal.mobile())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sales:create')")
    public ApiResponse<SalesOrderResponse> update(
            @PathVariable long id,
            @Valid @RequestBody SaveSalesOrderRequest request,
            @AuthenticationPrincipal ErpPrincipal principal
    ) {
        return ApiResponse.success(SalesOrderResponse.from(
                service.update(id, request.toCommand(), principal.mobile())
        ));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('sales:create')")
    public ApiResponse<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('sales:create')")
    public ApiResponse<SalesOrderResponse> confirm(
            @PathVariable long id,
            @AuthenticationPrincipal ErpPrincipal principal
    ) {
        return ApiResponse.success(SalesOrderResponse.from(service.confirm(id, principal.mobile())));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasAuthority('sales:create')")
    public ApiResponse<SalesOrderResponse> voidOrder(
            @PathVariable long id,
            @RequestBody VoidSalesOrderRequest request,
            @AuthenticationPrincipal ErpPrincipal principal
    ) {
        return ApiResponse.success(SalesOrderResponse.from(
                service.voidOrder(id, request == null ? null : request.reason(), principal.mobile())
        ));
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException("VALIDATION_FAILED", org.springframework.http.HttpStatus.BAD_REQUEST, "分页参数无效");
        }
    }
}
