package com.bebefish.erp.masterdata.api;

import com.bebefish.erp.common.api.*;
import com.bebefish.erp.masterdata.application.CustomerService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    private final CustomerService service;
    public CustomerController(CustomerService service) { this.service = service; }

    @GetMapping @PreAuthorize("hasAuthority('masterdata:view')")
    public ApiResponse<PageResponse<CustomerResponse>> list(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String status) {
        validatePage(page, size);
        var result = service.list(keyword, status, PageRequest.of(page - 1, size,
                Sort.by(Sort.Order.asc("customerName"), Sort.Order.asc("id")))).map(CustomerResponse::from);
        return ApiResponse.success(PageResponse.from(result));
    }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('masterdata:view')")
    public ApiResponse<CustomerResponse> get(@PathVariable long id) { return ApiResponse.success(CustomerResponse.from(service.get(id))); }
    @PostMapping @PreAuthorize("hasAuthority('masterdata:edit')")
    public ApiResponse<CustomerResponse> create(@Valid @RequestBody SaveCustomerRequest r) { return ApiResponse.success(CustomerResponse.from(service.create(r.toCommand()))); }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('masterdata:edit')")
    public ApiResponse<CustomerResponse> update(@PathVariable long id, @Valid @RequestBody SaveCustomerRequest r) { return ApiResponse.success(CustomerResponse.from(service.update(id, r.toCommand()))); }
    @PostMapping("/{id}/status") @PreAuthorize("hasAuthority('masterdata:edit')")
    public ApiResponse<CustomerResponse> status(@PathVariable long id, @Valid @RequestBody ChangeMasterdataStatusRequest r) { return ApiResponse.success(CustomerResponse.from(service.changeStatus(id, r.status()))); }
    private void validatePage(int page, int size) { if (page < 1 || size < 1 || size > 100) throw new BusinessException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "分页参数无效"); }
}
