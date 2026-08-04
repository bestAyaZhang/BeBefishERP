package com.bebefish.erp.masterdata.application;

public record SaveSupplierCommand(
        String number, String name, String contactPerson, String mobile, String telephone,
        String address, String remark
) {
}
