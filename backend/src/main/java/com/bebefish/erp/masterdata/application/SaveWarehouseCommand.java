package com.bebefish.erp.masterdata.application;

public record SaveWarehouseCommand(
        String number, String name, String address, boolean isDefault, String remark
) {}
