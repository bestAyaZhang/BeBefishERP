package com.bebefish.erp.masterdata.application;

public record SaveWarehouseCommand(
        String number, String name, String address, boolean isDefault, String remark,
        Boolean independentReplenishment
) {
    public SaveWarehouseCommand(String number, String name, String address, boolean isDefault, String remark) {
        this(number, name, address, isDefault, remark, null);
    }
}
