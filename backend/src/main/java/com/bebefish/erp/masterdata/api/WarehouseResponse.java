package com.bebefish.erp.masterdata.api;

import com.bebefish.erp.masterdata.domain.Warehouse;

public record WarehouseResponse(
        Long id,
        String warehouseNo,
        String warehouseName,
        String address,
        boolean defaultWarehouse,
        String status,
        String remark
) {
    static WarehouseResponse from(Warehouse warehouse) {
        return new WarehouseResponse(
                warehouse.id(), warehouse.number(), warehouse.name(), warehouse.address(),
                warehouse.isDefault(), warehouse.status(), warehouse.remark()
        );
    }
}
