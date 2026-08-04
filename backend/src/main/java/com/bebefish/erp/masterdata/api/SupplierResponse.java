package com.bebefish.erp.masterdata.api;

import com.bebefish.erp.masterdata.domain.Supplier;

public record SupplierResponse(
        Long id, String supplierNo, String supplierName, String contactPerson, String mobile,
        String telephone, String address, String status, String remark
) {
    static SupplierResponse from(Supplier v) {
        return new SupplierResponse(v.id(), v.number(), v.name(), v.contactPerson(), v.mobile(),
                v.telephone(), v.address(), v.status(), v.remark());
    }
}
