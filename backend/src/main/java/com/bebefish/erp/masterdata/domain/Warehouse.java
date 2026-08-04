package com.bebefish.erp.masterdata.domain;

public record Warehouse(
        Long id, String number, String name, String address,
        boolean isDefault, String status, String remark
) {}
