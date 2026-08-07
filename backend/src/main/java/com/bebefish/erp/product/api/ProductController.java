package com.bebefish.erp.product.api;

import com.bebefish.erp.common.api.ApiResponse;
import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.common.api.PageResponse;
import com.bebefish.erp.masterdata.api.ChangeMasterdataStatusRequest;
import com.bebefish.erp.product.application.ProductService;
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
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('product:view')")
    public ApiResponse<PageResponse<ProductResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String status
    ) {
        validatePage(page, size);
        var products = service.listProducts(
                keyword, categoryId, supplierId, status,
                PageRequest.of(page - 1, size, Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")))
        ).map(this::toResponse);
        return ApiResponse.success(PageResponse.from(products));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('product:view')")
    public ApiResponse<ProductResponse> get(@PathVariable long id) {
        return ApiResponse.success(toResponse(service.getProduct(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('product:edit')")
    public ApiResponse<ProductResponse> create(@Valid @RequestBody SaveProductRequest request) {
        return ApiResponse.success(toResponse(service.createProduct(request.toCommand())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('product:edit')")
    public ApiResponse<ProductResponse> update(
            @PathVariable long id,
            @Valid @RequestBody SaveProductRequest request
    ) {
        return ApiResponse.success(toResponse(service.updateProduct(id, request.toCommand())));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('product:edit')")
    public ApiResponse<ProductResponse> changeStatus(
            @PathVariable long id,
            @Valid @RequestBody ChangeMasterdataStatusRequest request
    ) {
        return ApiResponse.success(toResponse(service.changeStatus(id, request.status())));
    }

    private ProductResponse toResponse(com.bebefish.erp.product.domain.Product product) {
        return ProductResponse.from(product, service.imageUrls(product.id()), service.defaultSupplierName(product.id()));
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "分页参数无效");
        }
    }
}
