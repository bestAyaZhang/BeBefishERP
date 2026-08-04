package com.bebefish.erp.masterdata.api;

import com.bebefish.erp.masterdata.application.SaveWarehouseCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveWarehouseRequest(
        @Size(max = 50) String warehouseNo,
        @NotBlank @Size(max = 100) String warehouseName,
        @Size(max = 500) String address,
        boolean defaultWarehouse,
        @Size(max = 500) String remark
) {
    SaveWarehouseCommand toCommand() {
        return new SaveWarehouseCommand(
                warehouseNo, warehouseName, address, defaultWarehouse, remark
        );
    }
}
