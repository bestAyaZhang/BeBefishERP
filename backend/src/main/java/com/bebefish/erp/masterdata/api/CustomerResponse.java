package com.bebefish.erp.masterdata.api;

import com.bebefish.erp.masterdata.domain.Customer;

public record CustomerResponse(
        Long id, String customerNo, String customerName, String contactPerson, String mobile, String telephone,
        String province, String city, String district, String detailAddress,
        String transportMethod, String settlementCycle, boolean system, String status, String remark
) {
    static CustomerResponse from(Customer v) {
        return new CustomerResponse(v.id(), v.number(), v.name(), v.contactPerson(), v.mobile(), v.telephone(),
                v.province(), v.city(), v.district(), v.detailAddress(), v.transportMethod(), v.settlementCycle(),
                v.system(), v.status(), v.remark());
    }
}
