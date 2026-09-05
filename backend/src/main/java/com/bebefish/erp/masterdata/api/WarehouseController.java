package com.bebefish.erp.masterdata.api;

import com.bebefish.erp.common.api.ApiResponse;
import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.common.api.PageResponse;
import com.bebefish.erp.masterdata.application.WarehouseService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/warehouses")
public class WarehouseController {
    private final WarehouseService service;

    public WarehouseController(WarehouseService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('warehouse:view')")
    public ApiResponse<PageResponse<WarehouseResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status
    ) {
        validatePage(page, size);
        var warehouses = service.list(
                keyword,
                status,
                PageRequest.of(
                        page - 1,
                        size,
                        Sort.by(Sort.Order.asc("warehouseName"), Sort.Order.asc("id"))
                )
        ).map(WarehouseResponse::from);
        return ApiResponse.success(PageResponse.from(warehouses));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('warehouse:view')")
    public ApiResponse<WarehouseResponse> get(@PathVariable long id) {
        return ApiResponse.success(WarehouseResponse.from(service.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('warehouse:create')")
    public ApiResponse<WarehouseResponse> create(
            @Valid @RequestBody SaveWarehouseRequest request
    ) {
        return ApiResponse.success(WarehouseResponse.from(service.create(request.toCommand())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('warehouse:edit')")
    public ApiResponse<WarehouseResponse> update(
            @PathVariable long id,
            @Valid @RequestBody SaveWarehouseRequest request
    ) {
        return ApiResponse.success(WarehouseResponse.from(service.update(id, request.toCommand())));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('warehouse:edit')")
    public ApiResponse<WarehouseResponse> changeStatus(
            @PathVariable long id,
            @Valid @RequestBody ChangeMasterdataStatusRequest request
    ) {
        return ApiResponse.success(WarehouseResponse.from(
                service.changeStatus(id, request.status())
        ));
    }

    @PostMapping("/{id}/default")
    @PreAuthorize("hasAuthority('warehouse:edit')")
    public ApiResponse<WarehouseResponse> setDefault(@PathVariable long id) {
        return ApiResponse.success(WarehouseResponse.from(service.setDefault(id)));
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException(
                    "VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "分页参数无效"
            );
        }
    }
}
