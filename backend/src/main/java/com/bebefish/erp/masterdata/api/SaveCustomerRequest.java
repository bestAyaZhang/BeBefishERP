package com.bebefish.erp.masterdata.api;

import com.bebefish.erp.masterdata.application.SaveCustomerCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveCustomerRequest(
        @Size(max = 50) String customerNo,
        @NotBlank @Size(max = 100) String customerName,
        @Size(max = 100) String contactPerson,
        @Size(max = 30) String mobile,
        @Size(max = 30) String telephone,
        @Size(max = 100) String province,
        @Size(max = 100) String city,
        @Size(max = 100) String district,
        @Size(max = 500) String detailAddress,
        @NotBlank String transportMethod,
        @NotBlank String settlementCycle,
        @Size(max = 500) String remark
) {
    SaveCustomerCommand toCommand() {
        return new SaveCustomerCommand(customerNo, customerName, contactPerson, mobile, telephone,
                province, city, district, detailAddress, transportMethod, settlementCycle, remark);
    }
}
