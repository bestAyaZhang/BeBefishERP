package com.bebefish.erp.shipping.logistics;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.shipping.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AneOrderServiceTest {
    private final AneProperties config = new AneProperties();
    private final AneOrderStore store = mock(AneOrderStore.class);
    private final AneOrderClient client = mock(AneOrderClient.class);
    private final ShipmentRepository shipments = mock(ShipmentRepository.class);
    private AneOrderService service;
    @BeforeEach void setup() {
        config.setEnabled(true); config.setCode("code"); config.setAppKey("key"); config.setCustomerCode("customer");
        config.setCustomerPass("pass"); config.setDictId("dict"); config.setSenderName("测试寄件人");
        config.setSenderPhone("123"); config.setSenderProvince("浙江省"); config.setSenderCity("杭州市");
        config.setSenderCounty("萧山区"); config.setSenderAddress("测试路1号");
        service = new AneOrderService(config, store, client, shipments,
                Validation.buildDefaultValidatorFactory().getValidator());
        when(shipments.findById(1L)).thenReturn(Optional.of(shipment()));
    }
    @Test void returnsExistingSuccessWithoutSubmittingAgain() {
        var existing = new LogisticsOrder(1, "BF000000000001", "succeeded", "620240314001", "", "成功", LocalDateTime.now(), true);
        when(shipments.findById(1L)).thenReturn(Optional.of(shipment(2L)));
        when(store.claim(1L, 0L, "user")).thenReturn(new AneOrderStore.Claim(shipment(), existing.orderNo(), existing));
        assertThat(service.place(1, input(), "user")).isEqualTo(existing);
        verifyNoInteractions(client);
    }
    @Test void exposesConfiguredSenderWithoutExposingCredentials() {
        var availability = service.availability();

        assertThat(availability.sender()).isEqualTo(new AneOrderService.SenderInfo(
                "测试寄件人", "123", "浙江省", "杭州市", "萧山区", "测试路1号"));
        assertThat(availability.toString()).doesNotContain("customer", "pass", "key");
    }
    @Test void doesNotRetryUnknownOrProcessingOrders() {
        var existing = new LogisticsOrder(1, "BF000000000001", "unknown", "", "", "待核实", LocalDateTime.now(), true);
        when(store.claim(1L, 0L, "user")).thenReturn(new AneOrderStore.Claim(shipment(), existing.orderNo(), existing));
        assertThat(service.place(1, input(), "user").state()).isEqualTo("unknown");
        verifyNoInteractions(client);
    }
    @Test void sendsDocumentedFieldsAndDoesNotSendPredictedFreightAsCharge() {
        when(store.claim(1L, 0L, "user")).thenReturn(new AneOrderStore.Claim(shipment(), "BF000000000001", null));
        var result = new AneOrderResult("succeeded", "620240314001", "", "成功");
        when(client.createOrder(any())).thenReturn(result);
        service.place(1, input(), "user");
        var payload = org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        verify(client).createOrder(payload.capture());
        assertThat(payload.getValue()).containsEntry("receiveMan", "林女士")
                .containsEntry("sendMan", "本单发货人")
                .containsEntry("sendPhone", "13900000000")
                .containsEntry("sendProvinceName", "浙江省")
                .containsEntry("sendCityName", "金华市")
                .containsEntry("sendCountyName", "东阳市")
                .containsEntry("detailAddress", "本单发货路2号")
                .containsEntry("receivePhone", "13800006028")
                .containsEntry("toProvinceName", "浙江省")
                .containsEntry("toCityName", "杭州市")
                .containsEntry("toCountyName", "余杭区")
                .containsEntry("toAddress", "示例路18号2栋101室")
                .containsEntry("cargoName", "水族用品")
                .containsEntry("pieceAmount", 2)
                .containsEntry("signSms", 0)
                .containsEntry("productTypeId", 524)
                .containsEntry("customerCode", "customer");
        assertThat(payload.getValue()).doesNotContainKey("transportFee");
        verify(store).finish(1, result, "user");
    }
    @Test void refusesDisabledConfigurationAndInvalidProductBeforeClaiming() {
        config.setEnabled(false);
        assertThatThrownBy(() -> service.place(1, input(), "user")).isInstanceOf(BusinessException.class);
        verifyNoInteractions(client, store);
    }

    @Test void rejectsIncompletePersistedDraftBeforeClaiming() {
        when(shipments.findById(1L)).thenReturn(Optional.of(shipment(null)));

        assertThatThrownBy(() -> service.place(1, input(), "user"))
                .isInstanceOfSatisfying(BusinessException.class,
                        failure -> assertThat(failure.code()).isEqualTo("VALIDATION_FAILED"));

        verifyNoInteractions(client, store);
    }

    private PlaceAneOrderRequest input() { return new PlaceAneOrderRequest(0L); }
    private Shipment shipment() {
        return shipment(new BigDecimal("0.10"), 0L);
    }
    private Shipment shipment(BigDecimal volume) {
        return shipment(volume, 0L);
    }
    private Shipment shipment(long version) {
        return shipment(new BigDecimal("0.10"), version);
    }
    private Shipment shipment(BigDecimal volume, long version) {
        var form = new ShipmentFormInput("淘宝", "测试店铺", java.util.List.of(), "林女士", "13800006028",
                "浙江省", "杭州市", "余杭区", "示例路18号2栋101室", "蓝色盒子2个", "", new BigDecimal("50"),
                new AneOrderDraft("水族用品", "纸箱", new BigDecimal("2.50"), volume,
                        2, 524, 180, 102, ""), "本单发货人", "13900000000", "浙江省", "金华市", "东阳市",
                "本单发货路2号");
        var content = ShipmentContent.from(LocalDate.now(), form, "unfinished", "测试用户", "", "");
        return new Shipment(1L,"FHtest",content,version,"user","user",LocalDateTime.now(),LocalDateTime.now());
    }
}
