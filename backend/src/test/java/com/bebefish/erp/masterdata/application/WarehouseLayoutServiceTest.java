package com.bebefish.erp.masterdata.application;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.inventory.application.WarehousePileReleaseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WarehouseLayoutServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private WarehouseLayoutService.Layout layout(long revision) throws Exception {
        return new WarehouseLayoutService.Layout(revision,mapper.readTree("""
            {"schemaVersion":1,"structure":{"outline":{"nodes":[]},"partitions":[],"doors":[]},"palletGroups":[],"completed":false}
            """));
    }
    @Test void acceptsDraftAndRejectsUnknownSchema() throws Exception {
        WarehouseLayoutService.validate(layout(0));
        assertThrows(BusinessException.class, () -> WarehouseLayoutService.validate(new WarehouseLayoutService.Layout(0,mapper.readTree("{\"schemaVersion\":2}"))));
        assertThrows(BusinessException.class, () -> WarehouseLayoutService.validate(layout(-1)));
    }
    @SuppressWarnings("unchecked")
    @Test void rejectsStaleRevisionBeforeWriting() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var warehouses = mock(WarehouseService.class);
        when(jdbc.query(anyString(),any(RowMapper.class),eq(7L))).thenReturn(List.of(layout(3)));
        var service = new WarehouseLayoutService(jdbc,mapper,warehouses,mock(WarehousePileReleaseService.class));
        assertThrows(BusinessException.class, () -> service.save(7,layout(2)));
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @SuppressWarnings("unchecked")
    @Test void createsFirstRevisionForAnExistingWarehouse() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var warehouses = mock(WarehouseService.class);
        when(jdbc.query(anyString(),any(RowMapper.class),eq(7L))).thenReturn(List.of());
        var service = new WarehouseLayoutService(jdbc,mapper,warehouses,mock(WarehousePileReleaseService.class));
        assertEquals(1,service.save(7,layout(0)).revision());
        verify(warehouses).get(7);
        verify(jdbc).update(startsWith("INSERT INTO warehouse_layout"),eq(7L),eq(1L),anyString());
    }
}
