package com.bebefish.erp.masterdata.infrastructure;

import com.bebefish.erp.masterdata.domain.Warehouse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "warehouse")
public class WarehouseJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "warehouse_no", nullable = false, length = 50)
    private String warehouseNo;

    @Column(name = "warehouse_name", nullable = false, length = 100)
    private String warehouseName;

    @Column(length = 500)
    private String address;

    @Column(name = "is_default", nullable = false)
    private boolean defaultWarehouse;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 500)
    private String remark;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected WarehouseJpaEntity() {
    }

    WarehouseJpaEntity(Warehouse warehouse) {
        apply(warehouse);
    }

    void apply(Warehouse warehouse) {
        warehouseNo = warehouse.number();
        warehouseName = warehouse.name();
        address = warehouse.address();
        defaultWarehouse = warehouse.isDefault();
        status = warehouse.status();
        remark = warehouse.remark();
    }

    Warehouse toDomain() {
        return new Warehouse(
                id, warehouseNo, warehouseName, address,
                defaultWarehouse, status, remark
        );
    }

    @PrePersist
    void prePersist() {
        var now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
