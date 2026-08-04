package com.bebefish.erp.masterdata.application;

public record SaveCategoryCommand(
        String code,
        String name,
        int sortOrder,
        String remark
) {
}
