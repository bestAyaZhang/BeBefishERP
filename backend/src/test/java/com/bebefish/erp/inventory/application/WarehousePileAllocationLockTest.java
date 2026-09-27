package com.bebefish.erp.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class WarehousePileAllocationLockTest {
    @Test
    void locksTheWarehouseBeforeReadingGeometryOrLockingInventory() {
        var jdbc = mock(NamedParameterJdbcTemplate.class);
        var statements = new ArrayList<String>();
        when(jdbc.query(anyString(), anyMap(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            statements.add(sql.toLowerCase());
            if (sql.contains("layout_json")) return List.of("""
                    {"completed":true,"palletGroups":[{"id":"pile","left":0,"top":0,"width":2,"height":2}]}
                    """);
            if (sql.contains("from warehouse ")) return List.of(8L);
            if (sql.contains("from inventory_balance")) return List.of(new BigDecimal("100"));
            return List.of();
        });
        var service = new WarehousePileAllocationService(jdbc, mock(WarehouseInventoryLayoutQueryService.class), new ObjectMapper());
        service.allocate(8, new WarehousePileAllocationService.Command("pile", 101, 24));

        assertThat(statements.getFirst()).contains("from warehouse ", "for update");
        assertThat(statements.get(1)).contains("layout_json", "for update");
        assertThat(statements.get(2)).contains("from inventory_balance", "for update");
        assertThat(statements.get(3)).contains("inventory_location_balance", "for update");
    }
}
