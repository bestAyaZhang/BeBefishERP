package com.bebefish.erp.shipping.logistics;

import com.bebefish.erp.shipping.domain.ShipmentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AneOrderStoreTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void onlyProductionSuccessWritesARealShipmentTrackingNumber(boolean testEnvironment) {
        var jdbc = mock(NamedParameterJdbcTemplate.class);
        var store = spy(new AneOrderStore(jdbc, mock(ShipmentRepository.class), new ObjectMapper(), new AneProperties()));
        doReturn(Optional.of(new LogisticsOrder(1, "BF123", "processing", "", "", "", LocalDateTime.now(), testEnvironment))).when(store).find(1);
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);
        var input = new PlaceAneOrderRequest(0L, "浙江省", "杭州市", "萧山区", "示例路1号", BigDecimal.TEN, BigDecimal.ONE, 1, "盒子", "纸箱", 524, 180, 102, "");
        store.finish(1, input, new AneOrderResult("succeeded", "620240314001", "", "成功"), "operator");
        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeastOnce()).update(sql.capture(), any(SqlParameterSource.class));
        assertThat(sql.getAllValues().stream().anyMatch(s -> s.contains("update shipment set tracking_no"))).isEqualTo(!testEnvironment);
    }
}
