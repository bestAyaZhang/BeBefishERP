package com.bebefish.erp.shipping.logistics;

import com.bebefish.erp.shipping.domain.ShipmentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AneOrderStoreTest {
    @Test
    void repeatClaimsReturnTheReservedOrderWithoutWritingAgain() {
        var jdbc = mock(NamedParameterJdbcTemplate.class);
        var shipments = mock(ShipmentRepository.class);
        when(jdbc.queryForList(anyString(), anyMap(), eq(Long.class))).thenReturn(java.util.List.of(1L));
        when(shipments.findById(1L)).thenReturn(Optional.of(shipment()));
        var existing = new LogisticsOrder(1, "BF123", "processing", "", "", "提交中", LocalDateTime.now(), false);
        var store = spy(new AneOrderStore(jdbc, shipments, new ObjectMapper(), new AneProperties()));
        doReturn(Optional.of(existing)).when(store).find(1L);

        assertThat(store.claim(1, 1, "operator").existing()).isEqualTo(existing);
        assertThat(store.claim(1, 1, "operator").existing()).isEqualTo(existing);

        verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
    }

    @Test
    void versionMismatchStopsBeforeReservationWrite() {
        var jdbc = mock(NamedParameterJdbcTemplate.class);
        var shipments = mock(ShipmentRepository.class);
        when(jdbc.queryForList(anyString(), anyMap(), eq(Long.class))).thenReturn(java.util.List.of(1L));
        when(shipments.findById(1L)).thenReturn(Optional.of(shipment()));
        var store = spy(new AneOrderStore(jdbc, shipments, new ObjectMapper(), new AneProperties()));
        doReturn(Optional.empty()).when(store).find(1L);

        assertThatThrownBy(() -> store.claim(1, 0, "operator"))
                .isInstanceOf(com.bebefish.erp.common.api.BusinessException.class);

        verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void successfulOrderUpdatesTrackingAndCompletionInEveryEnvironment(boolean testEnvironment) {
        var jdbc = mock(NamedParameterJdbcTemplate.class);
        var shipments = mock(ShipmentRepository.class);
        when(shipments.findById(1L)).thenReturn(Optional.of(shipment()));
        var store = spy(new AneOrderStore(jdbc, shipments, new ObjectMapper(), new AneProperties()));
        doReturn(Optional.of(new LogisticsOrder(1, "BF123", "processing", "", "", "", LocalDateTime.now(), testEnvironment))).when(store).find(1);
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);
        store.finish(1, new AneOrderResult("succeeded", "620240314001", "", "成功"), "operator");
        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeastOnce()).update(sql.capture(), any(SqlParameterSource.class));
        assertThat(sql.getAllValues()).anyMatch(s -> s.contains("update shipment set tracking_no")
                && s.contains("logistics_company='安能物流'") && s.contains("status='completed'"));
    }

    private com.bebefish.erp.shipping.domain.Shipment shipment() {
        var draft = new com.bebefish.erp.shipping.domain.AneOrderDraft("盒子", "纸箱",
                java.math.BigDecimal.TEN, java.math.BigDecimal.ONE, 1, 524, 180, 104, "");
        var form = new com.bebefish.erp.shipping.domain.ShipmentFormInput("淘宝", "测试店铺", java.util.List.of(),
                "收件人", "13800138000", "浙江省", "杭州市", "余杭区", "示例路1号", "盒子1个", "",
                null, draft);
        var content = com.bebefish.erp.shipping.domain.ShipmentContent.from(LocalDate.now(), form,
                "unfinished", "操作人", "", "");
        return new com.bebefish.erp.shipping.domain.Shipment(1L, "FHtest", content, 1L, "user", "user",
                LocalDateTime.now(), LocalDateTime.now());
    }
}
