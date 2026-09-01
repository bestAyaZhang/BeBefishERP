package com.bebefish.erp.product.api;

import com.bebefish.erp.common.api.ApiResponse;
import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.common.api.PageResponse;
import com.bebefish.erp.file.domain.FileAccessUrlResolver;
import com.bebefish.erp.masterdata.api.ChangeMasterdataStatusRequest;
import com.bebefish.erp.product.application.ProductCatalogMetrics;
import com.bebefish.erp.product.application.ProductCatalogQueryService;
import com.bebefish.erp.product.application.ProductService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
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
    private final ProductCatalogQueryService catalogQueryService;
    private final FileAccessUrlResolver urlResolver;

    public ProductController(
            ProductService service,
            ProductCatalogQueryService catalogQueryService,
            FileAccessUrlResolver urlResolver
    ) {
        this.service = service;
        this.catalogQueryService = catalogQueryService;
        this.urlResolver = urlResolver;
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
        );
        var metrics = catalogQueryService.load(products.getContent());
        return ApiResponse.success(PageResponse.from(products.map(
                product -> toResponse(product, metrics.get(product.id()))
        )));
    }

    @GetMapping("/category-counts")
    @PreAuthorize("hasAuthority('product:view')")
    public ApiResponse<Map<Long, Long>> categoryCounts() {
        return ApiResponse.success(catalogQueryService.categoryCounts());
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
        var metrics = catalogQueryService.load(List.of(product)).get(product.id());
        return toResponse(product, metrics);
    }

    private ProductResponse toResponse(
            com.bebefish.erp.product.domain.Product product,
            ProductCatalogMetrics metrics
    ) {
        var imageUrls = metrics.imageUrls().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, entry -> urlResolver.resolve(entry.getValue())
                ));
        return ProductResponse.from(product, metrics, imageUrls);
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "分页参数无效");
        }
    }
}
