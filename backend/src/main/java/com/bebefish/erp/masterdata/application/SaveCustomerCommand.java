package com.bebefish.erp.masterdata.application;

public record SaveCustomerCommand(
        String number, String name, String contactPerson, String mobile, String telephone,
        String province, String city, String district, String detailAddress,
        String transportMethod, String settlementCycle, String remark
) {
}
