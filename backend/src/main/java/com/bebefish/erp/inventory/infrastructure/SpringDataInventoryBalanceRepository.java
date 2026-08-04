package com.bebefish.erp.inventory.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataInventoryBalanceRepository extends JpaRepository<InventoryBalanceJpaEntity, Long> {
    @Query("""
            select balance.skuId
            from InventoryBalanceJpaEntity balance
            where balance.warehouseId = :warehouseId
              and balance.skuId in :skuIds
            """)
    List<Long> findExistingSkuIds(@Param("warehouseId") long warehouseId, @Param("skuIds") List<Long> skuIds);

    @Modifying
    @Query(value = """
            insert ignore into inventory_balance
                (warehouse_id, sku_id, quantity, version_no, created_at, updated_at)
            values (:warehouseId, :skuId, 0, 0, current_timestamp(3), current_timestamp(3))
            """, nativeQuery = true)
    int insertIfAbsent(@Param("warehouseId") long warehouseId, @Param("skuId") long skuId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select balance
            from InventoryBalanceJpaEntity balance
            where balance.warehouseId = :warehouseId
              and balance.skuId in :skuIds
            order by balance.skuId asc
            """)
    List<InventoryBalanceJpaEntity> findLockedByWarehouseIdAndSkuIds(
            @Param("warehouseId") long warehouseId,
            @Param("skuIds") List<Long> skuIds
    );
}
