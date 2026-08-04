package com.bebefish.erp.masterdata.domain;

public record Category(
        Long id,
        String code,
        String name,
        Long parentId,
        int level,
        int sortOrder,
        String status,
        String remark
) {
}
