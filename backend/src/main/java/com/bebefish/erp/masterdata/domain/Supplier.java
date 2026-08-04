package com.bebefish.erp.masterdata.domain;

public record Supplier(
        Long id, String number, String name, String contactPerson, String mobile, String telephone,
        String address, String status, String remark
) {
}
