package com.bebefish.erp.masterdata.api;

import com.bebefish.erp.common.api.ApiResponse;
import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.common.api.PageResponse;
import com.bebefish.erp.masterdata.application.CategoryService;
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
@RequestMapping("/api/categories")
public class CategoryController {
    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('masterdata:view')")
    public ApiResponse<PageResponse<CategoryResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status
    ) {
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException(
                    "VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "分页参数无效"
            );
        }
        var pageable = PageRequest.of(
                page - 1,
                size,
                Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("id"))
        );
        var categories = service.list(keyword, status, pageable).map(CategoryResponse::from);
        return ApiResponse.success(PageResponse.from(categories));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('masterdata:view')")
    public ApiResponse<CategoryResponse> get(@PathVariable long id) {
        return ApiResponse.success(CategoryResponse.from(service.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('masterdata:edit')")
    public ApiResponse<CategoryResponse> create(@Valid @RequestBody SaveCategoryRequest request) {
        return ApiResponse.success(CategoryResponse.from(service.create(request.toCommand())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('masterdata:edit')")
    public ApiResponse<CategoryResponse> update(
            @PathVariable long id,
            @Valid @RequestBody SaveCategoryRequest request
    ) {
        return ApiResponse.success(CategoryResponse.from(service.update(id, request.toCommand())));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('masterdata:edit')")
    public ApiResponse<CategoryResponse> changeStatus(
            @PathVariable long id,
            @Valid @RequestBody ChangeCategoryStatusRequest request
    ) {
        return ApiResponse.success(CategoryResponse.from(service.changeStatus(id, request.status())));
    }
}
