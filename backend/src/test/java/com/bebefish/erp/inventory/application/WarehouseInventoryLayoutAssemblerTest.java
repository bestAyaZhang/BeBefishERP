package com.bebefish.erp.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bebefish.erp.common.api.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class WarehouseInventoryLayoutAssemblerTest {
    private final WarehouseInventoryLayoutAssembler assembler = new WarehouseInventoryLayoutAssembler();
    private final LocalDateTime timestamp = LocalDateTime.of(2026, 9, 10, 10, 30);

    @Test
    void keepsWarehouseBalanceAuthoritativeAndAddsOnlyTheUnallocatedRemainder() {
        var balances = List.of(
                balance(101, "SKU-A", "矿物水", "蓝标", 24, "300"),
                balance(102, "SKU-B", "冷饮杯", "透明", 50, "80")
        );
        var locations = List.of(
                allocation("zone-a", "pile-a", 101, "SKU-A", "矿物水", "蓝标", 24, "120"),
                allocation("zone-a", "pile-a", 102, "SKU-B", "冷饮杯", "透明", 50, "80")
        );

        var result = assembler.assemble(8, balances, locations);

        assertThat(result.totalUnits()).isEqualByComparingTo("380");
        assertThat(result.placedUnits()).isEqualByComparingTo("200");
        assertThat(result.unallocatedUnits()).isEqualByComparingTo("180");
        assertThat(result.skuCount()).isEqualTo(2);
        assertThat(result.allocations()).hasSize(3);
        assertThat(result.allocations()).anySatisfy(item -> {
            assertThat(item.palletId()).isEqualTo("UNALLOCATED");
            assertThat(item.zoneId()).isNull();
            assertThat(item.skuId()).isEqualTo(101);
            assertThat(item.units()).isEqualByComparingTo("180");
        });
    }

    @Test
    void rejectsLocationQuantityThatExceedsTheRealWarehouseBalance() {
        var balances = List.of(balance(101, "SKU-A", "矿物水", "蓝标", 24, "100"));
        var locations = List.of(allocation("zone-a", "pile-a", 101, "SKU-A", "矿物水", "蓝标", 24, "120"));

        assertThatThrownBy(() -> assembler.assemble(8, balances, locations))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.code()).isEqualTo("INVENTORY_LOCATION_INCONSISTENT");
                    assertThat(error.getMessage()).contains("SKU-A");
                });
    }

    private WarehouseInventoryLayoutAssembler.BalanceRow balance(
            long skuId, String skuCode, String productName, String skuName, Integer unitsPerCase, String units
    ) {
        return new WarehouseInventoryLayoutAssembler.BalanceRow(
                skuId, skuCode, productName, skuName, null, unitsPerCase, new BigDecimal(units), timestamp
        );
    }

    private WarehouseInventoryLayoutAssembler.LocationRow allocation(
            String zoneId, String palletId, long skuId, String skuCode, String productName,
            String skuName, Integer unitsPerCase, String units
    ) {
        return new WarehouseInventoryLayoutAssembler.LocationRow(
                zoneId, palletId, skuId, skuCode, productName, skuName, null,
                unitsPerCase, new BigDecimal(units), timestamp
        );
    }
}
