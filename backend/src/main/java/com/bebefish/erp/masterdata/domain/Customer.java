package com.bebefish.erp.masterdata.domain;

public record Customer(
        Long id, String number, String name, String contactPerson, String mobile, String telephone,
        String province, String city, String district, String detailAddress,
        String transportMethod, String settlementCycle,
        boolean system, String status, String remark
) {
}
