package com.bebefish.erp.masterdata.api;

import com.bebefish.erp.masterdata.domain.Category;

public record CategoryResponse(
        Long id,
        String categoryCode,
        String categoryName,
        Long parentId,
        int level,
        int sortOrder,
        String status,
        String remark
) {
    static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.id(), category.code(), category.name(), category.parentId(), category.level(),
                category.sortOrder(), category.status(), category.remark()
        );
    }
}
