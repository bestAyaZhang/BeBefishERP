package com.bebefish.erp.masterdata.api;

import com.bebefish.erp.masterdata.application.SaveSupplierCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveSupplierRequest(
        @Size(max = 50) String supplierNo,
        @NotBlank @Size(max = 100) String supplierName,
        @Size(max = 100) String contactPerson,
        @Size(max = 30) String mobile,
        @Size(max = 30) String telephone,
        @Size(max = 500) String address,
        @Size(max = 500) String remark
) {
    SaveSupplierCommand toCommand() {
        return new SaveSupplierCommand(supplierNo, supplierName, contactPerson, mobile, telephone, address, remark);
    }
}
