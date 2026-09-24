package com.bebefish.erp.shipping.logistics;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.shipping.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AneOrderServiceTest {
    private final AneProperties config = new AneProperties();
    private final AneOrderStore store = mock(AneOrderStore.class);
    private final AneOrderClient client = mock(AneOrderClient.class);
    private AneOrderService service;
    @BeforeEach void setup() {
        config.setEnabled(true); config.setCode("code"); config.setAppKey("key"); config.setCustomerCode("customer");
        config.setCustomerPass("pass"); config.setDictId("dict"); config.setSenderName("测试寄件人");
        config.setSenderPhone("123"); config.setSenderProvince("浙江省"); config.setSenderCity("杭州市");
        config.setSenderCounty("萧山区"); config.setSenderAddress("测试路1号");
        service = new AneOrderService(config, store, client, Validation.buildDefaultValidatorFactory().getValidator());
    }
    @Test void returnsExistingSuccessWithoutSubmittingAgain() {
        var existing = new LogisticsOrder(1, "BF000000000001", "succeeded", "620240314001", "", "成功", LocalDateTime.now(), true);
        when(store.claim(eq(1L), any(), eq("user"))).thenReturn(new AneOrderStore.Claim(shipment(), existing.orderNo(), existing));
        assertThat(service.place(1, input(), "user")).isEqualTo(existing);
        verifyNoInteractions(client);
    }
    @Test void doesNotRetryUnknownOrProcessingOrders() {
        var existing = new LogisticsOrder(1, "BF000000000001", "unknown", "", "", "待核实", LocalDateTime.now(), true);
        when(store.claim(eq(1L), any(), eq("user"))).thenReturn(new AneOrderStore.Claim(shipment(), existing.orderNo(), existing));
        assertThat(service.place(1, input(), "user").state()).isEqualTo("unknown");
        verifyNoInteractions(client);
    }
    @Test void sendsDocumentedFieldsAndDoesNotSendPredictedFreightAsCharge() {
        when(store.claim(eq(1L), any(), eq("user"))).thenReturn(new AneOrderStore.Claim(shipment(), "BF000000000001", null));
        var result = new AneOrderResult("succeeded", "620240314001", "", "成功");
        when(client.createOrder(any())).thenReturn(result);
        service.place(1, input(), "user");
        var payload = org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        verify(client).createOrder(payload.capture());
        assertThat(payload.getValue()).containsEntry("receiveMan", "测试收件人").containsEntry("signSms", 0)
                .containsEntry("pieceAmount", 2).containsEntry("productTypeId", 524).containsEntry("customerCode", "customer");
        assertThat(payload.getValue()).doesNotContainKey("transportFee");
        verify(store).finish(1, input(), result, "user");
    }
    @Test void refusesDisabledConfigurationAndInvalidProductBeforeClaiming() {
        config.setEnabled(false);
        assertThatThrownBy(() -> service.place(1, input(), "user")).isInstanceOf(BusinessException.class);
        verifyNoInteractions(client, store);
    }
    private PlaceAneOrderRequest input() { return new PlaceAneOrderRequest(0L, "浙江省", "杭州市", "萧山区", "示例路2号",
            new BigDecimal("2.50"), new BigDecimal("0.10"), 2, "收纳盒", "纸箱", 524, 180, 102, ""); }
    private Shipment shipment() {
        var form = new ShipmentFormInput("淘宝", "测试店铺", java.util.List.of(), "测试收件人", "123",
                "浙江省", "杭州市", "萧山区", "测试地址", "蓝色盒子2个", "", new BigDecimal("50"),
                new AneOrderDraft("收纳盒", "纸箱", new BigDecimal("2.50"), new BigDecimal("0.10"),
                        2, 524, 180, 102, ""));
        var content = ShipmentContent.from(LocalDate.now(), form, "unfinished", "测试用户", "", "");
        return new Shipment(1L,"FHtest",content,0,"user","user",LocalDateTime.now(),LocalDateTime.now());
    }
}
