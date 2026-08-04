package com.bebefish.erp.masterdata.infrastructure;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SpringDataWarehouseRepository extends
        JpaRepository<WarehouseJpaEntity, Long>,
        JpaSpecificationExecutor<WarehouseJpaEntity> {
    boolean existsByWarehouseNo(String warehouseNo);

    boolean existsByWarehouseNoAndIdNot(String warehouseNo, Long id);

    boolean existsByWarehouseName(String warehouseName);

    boolean existsByWarehouseNameAndIdNot(String warehouseName, Long id);

    Optional<WarehouseJpaEntity> findFirstByDefaultWarehouseTrueAndStatus(String status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update WarehouseJpaEntity warehouse set warehouse.defaultWarehouse = false "
            + "where warehouse.defaultWarehouse = true")
    int clearDefault();
}
