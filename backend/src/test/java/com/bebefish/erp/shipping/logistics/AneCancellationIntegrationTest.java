package com.bebefish.erp.shipping.logistics;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.shipping.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class AneCancellationIntegrationTest {
    @Autowired AneOrderStore store;
    @Autowired ShipmentRepository shipments;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired Validator validator;

    @Test void successClearsOnlyTheActiveWaybillAndPreservesPreparationAndOriginalOrder() throws Exception {
        long id = seed(true);
        var client = mock(AneOrderClient.class);
        when(client.cancelOrder(any())).thenReturn(new AneCancellationResult("cancelled", "取消成功"));
        var result = service(client).cancel(id, new CancelAneOrderRequest(3L), "canceller");
        assertThat(result.state()).isEqualTo("cancelled");
        assertThat(result.trackingNo()).isEqualTo("620240314001");
        var shipment = shipments.findById(id).orElseThrow();
        assertThat(shipment.content().trackingNo()).isEmpty();
        assertThat(shipment.content().status()).isEqualTo("partially_shipped");
        assertThat(shipment.logisticsOrderState()).isEqualTo("cancelled");
        assertThat(shipment.updatedBy()).isEqualTo("canceller");
        var payload = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(client).cancelOrder(payload.capture());
        assertThat(payload.getValue()).containsEntry("action", 10).containsEntry("ewbNo", "")
                .containsEntry("sendMan", "原寄件人").containsEntry("receiveMan", "原收件人")
                .containsEntry("pieceAmount", 2).containsEntry("dictId", "000070");
        assertThat(service(client).cancel(id, new CancelAneOrderRequest(3L), "other").state()).isEqualTo("cancelled");
        assertThat(store.claim(id, shipment.version(), "other").existing().state()).isEqualTo("cancelled");
        verify(client, times(1)).cancelOrder(any());
    }

    @Test void refusalKeepsTheWaybillAndAllowsAnExplicitNewAttempt() throws Exception {
        long id = seed(true);
        var client = mock(AneOrderClient.class);
        when(client.cancelOrder(any())).thenReturn(new AneCancellationResult("cancel_rejected", "已揽收，取消失败"));
        assertThat(service(client).cancel(id, new CancelAneOrderRequest(3L), "user").state()).isEqualTo("cancel_rejected");
        var shipment = shipments.findById(id).orElseThrow();
        assertThat(shipment.content().trackingNo()).isEqualTo("620240314001");
        assertThat(shipment.content().status()).isEqualTo("partially_shipped");
        assertThat(store.claimCancellation(id, shipment.version(), "retry").claimed()).isTrue();
    }

    @Test void ambiguousResultsCannotBeResubmittedAndExpiredReservationsRemainUncertain() throws Exception {
        long id = seed(true);
        var client = mock(AneOrderClient.class);
        when(client.cancelOrder(any())).thenReturn(new AneCancellationResult("cancel_unknown", "取消待核实"));
        var service = service(client);
        assertThat(service.cancel(id, new CancelAneOrderRequest(3L), "user").state()).isEqualTo("cancel_unknown");
        assertThat(service.cancel(id, new CancelAneOrderRequest(3L), "user").state()).isEqualTo("cancel_unknown");
        verify(client, times(1)).cancelOrder(any());
        assertThat(shipments.findById(id).orElseThrow().content().trackingNo()).isEqualTo("620240314001");
        jdbc.update("update shipment_logistics_order set state='cancel_processing', updated_at=now(3)-interval 3 minute where shipment_id=?", id);
        assertThat(store.find(id).orElseThrow().state()).isEqualTo("cancel_unknown");
        assertThat(shipments.findById(id).orElseThrow().logisticsOrderState()).isEqualTo("cancel_unknown");
        assertThat(store.claimCancellation(id, 3L, "user").claimed()).isFalse();
    }

    @Test void mismatchedEnvironmentAndStaleVersionNeverReachTheCarrier() throws Exception {
        var client = mock(AneOrderClient.class);
        long formalId = seed(false), testId = seed(true);
        assertThatThrownBy(() -> service(client).cancel(formalId, new CancelAneOrderRequest(3L), "user"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("LOGISTICS_ENVIRONMENT_CHANGED"));
        assertThatThrownBy(() -> service(client).cancel(testId, new CancelAneOrderRequest(2L), "user"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("SHIPMENT_VERSION_CONFLICT"));
        verifyNoInteractions(client);
        assertThat(store.find(testId).orElseThrow().state()).isEqualTo("succeeded");
    }

    @Test void concurrentCancellationReservesOnceBeforeTheRemoteCall() throws Exception {
        long id = seed(true);
        var client = mock(AneOrderClient.class);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(client.cancelOrder(any())).thenAnswer(call -> {
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("取消调用未释放");
            return new AneCancellationResult("cancelled", "取消成功");
        });
        try (var executor = Executors.newSingleThreadExecutor()) {
            var service = service(client);
            var first = executor.submit(() -> service.cancel(id, new CancelAneOrderRequest(3L), "user"));
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(service.cancel(id, new CancelAneOrderRequest(3L), "other").state()).isEqualTo("cancel_processing");
            } finally { release.countDown(); }
            assertThat(first.get(10, TimeUnit.SECONDS).state()).isEqualTo("cancelled");
        }
        verify(client, times(1)).cancelOrder(any());
    }

    @Test void malformedSnapshotsAndUnconfirmedCreationCannotBeCancelled() throws Exception {
        long id = seed(true);
        var client = mock(AneOrderClient.class);
        jdbc.update("update shipment_logistics_order set request_snapshot='{}' where shipment_id=?", id);
        assertThatThrownBy(() -> service(client).cancel(id, new CancelAneOrderRequest(3L), "user")).isInstanceOf(BusinessException.class);
        assertThat(store.find(id).orElseThrow().state()).isEqualTo("succeeded");
        jdbc.update("update shipment_logistics_order set state='unknown' where shipment_id=?", id);
        assertThatThrownBy(() -> store.claimCancellation(id, 3L, "user")).isInstanceOf(BusinessException.class);
        verifyNoInteractions(client);
    }

    private AneOrderService service(AneOrderClient client) {
        var config = new AneProperties(); config.setEnabled(true); config.setCode("test"); config.setAppKey("key");
        config.setCustomerCode("customer"); config.setCustomerPass("pass"); config.setDictId("000070");
        config.setSenderName("配置寄件人"); config.setSenderPhone("13800138000"); config.setSenderProvince("浙江省");
        config.setSenderCity("金华市"); config.setSenderCounty("东阳市"); config.setSenderAddress("配置地址");
        return new AneOrderService(config, store, client, shipments, validator);
    }

    private long seed(boolean test) throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        var form = new ShipmentFormInput("淘宝", "测试店", List.of("备货员"), "原收件人", "13800138000", "广东省", "东莞市", "寮步镇", "测试收件路36号", "商品2箱", "", null,
                new AneOrderDraft("测试货物", "纸箱", BigDecimal.TEN, new BigDecimal("0.10"), 2, 524, 180, 102, ""),
                "原寄件人", "13900000000", "浙江省", "金华市", "东阳市", "原寄件路7号");
        var content = ShipmentContent.from(LocalDate.now(), form, "partially_shipped", "test", "安能物流", "620240314001");
        String snapshot = mapper.writeValueAsString(content);
        var now = java.time.LocalDateTime.now();
        long id = shipments.insert(new Shipment(null, "FH-CANCEL-" + suffix, content, 3L, "test", "test", now, now)).id();
        jdbc.update("""
                insert into shipment_logistics_order (shipment_id,order_no,state,test_environment,tracking_no,child_tracking_nos,message,request_snapshot,operator_id,created_at,updated_at)
                values (?,?,'succeeded',?,'620240314001','620240314001001','下单成功',?,'test',now(3),now(3))
                """, id, "BF" + suffix, test, snapshot);
        return id;
    }
}
