package com.bebefish.erp.shipping.application;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.shipping.domain.*;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShipmentServiceTest {
    private final ShipmentRepository repository = mock(ShipmentRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-24T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private ShipmentService service;

    @BeforeEach
    void setup() {
        service = new ShipmentService(repository, Validation.buildDefaultValidatorFactory().getValidator(), clock,
                new ShipmentEditPolicy(), com.bebefish.erp.support.TestShippingSources.resolver());
        when(repository.insert(any())).thenAnswer(call -> {
            Shipment s = call.getArgument(0);
            return new Shipment(1L, s.shipmentNo(), s.content(), 0, s.createdBy(), s.updatedBy(), s.createdAt(), s.updatedAt());
        });
    }

    @Test
    void createOwnsDateStatusAndOrdererAndNormalizesPreparers() {
        var saved = service.create(form(List.of(" 小周 ", "阿杰", "小周"), null, BigDecimal.ZERO),
                "employee:23", "陈小鱼");

        assertThat(saved.content().shipmentDate()).isEqualTo(LocalDate.of(2026, 9, 24));
        assertThat(saved.content().status()).isEqualTo("unfinished");
        assertThat(saved.content().orderer()).isEqualTo("陈小鱼");
        assertThat(saved.content().preparers()).containsExactly("小周", "阿杰");
        assertThat(saved.content().preparationContent()).isEqualTo("蓝色收纳盒 × 2\n纸箱 × 1");
        assertThat(saved.content().orderDraft().weight()).isNull();
        assertThat(saved.content().estimatedFreight()).isEqualByComparingTo("0");
        assertThat(saved.createdBy()).isEqualTo("employee:23");
    }

    @Test
    void rejectsShipmentWithoutAPreparer() {
        assertThatThrownBy(() -> service.create(form(List.of(), null, null), "employee:23", "陈小鱼"))
                .isInstanceOfSatisfying(BusinessException.class,
                        failure -> assertThat(failure.getMessage()).contains("至少选择一名备货人"));

        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unfinished", "completed", "out_of_stock", "partially_shipped"})
    void updateAcceptsAllFourManualStatuses(String status) {
        var original = existing("unfinished");
        when(repository.findByIdForUpdate(1)).thenReturn(Optional.of(original));
        when(repository.update(any(), eq(2L))).thenReturn(true);

        assertThat(service.update(1, form(List.of("小周"), null, null), status, 2, "employee:9")
                .content().status()).isEqualTo(status);
    }

    @Test
    void updatePreservesServerOwnedDateAndOrderer() {
        var original = existing("unfinished");
        when(repository.findByIdForUpdate(1)).thenReturn(Optional.of(original));
        when(repository.update(any(), eq(2L))).thenReturn(true);

        var updated = service.update(1, form(List.of("小周"), new BigDecimal("2.500"), null),
                "completed", 2, "employee:9");

        assertThat(updated.content().shipmentDate()).isEqualTo(LocalDate.of(2026, 9, 18));
        assertThat(updated.content().orderer()).isEqualTo("陈小鱼");
        assertThat(updated.content().status()).isEqualTo("completed");
        assertThat(updated.updatedBy()).isEqualTo("employee:9");
        assertThat(updated.version()).isEqualTo(3);
    }

    @Test
    void rejectsNegativeOrOverPreciseAmountsBeforePersisting() {
        assertThatThrownBy(() -> service.create(form(List.of(), new BigDecimal("-1"), null), "user", "用户"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.create(form(List.of(), null, new BigDecimal("1.001")), "user", "用户"))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void reportsMissingRecordAndConcurrentEdit() {
        when(repository.findById(99)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(99)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.status().value()).isEqualTo(404));

        var original = existing("unfinished");
        when(repository.findByIdForUpdate(1)).thenReturn(Optional.of(original));
        when(repository.update(any(), eq(2L))).thenReturn(false);
        assertThatThrownBy(() -> service.update(1, form(List.of("小周"), null, null), "completed", 2, "second"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.status().value()).isEqualTo(409));
    }

    @Test
    void rejectsReversedDatesAndUnknownFilterStatus() {
        assertThatThrownBy(() -> service.list(new ShipmentQuery(null, null, LocalDate.of(2026,9,18), LocalDate.of(2026,9,17), null, false), 1, 20))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.list(new ShipmentQuery(null, "bad", null, null, null, false), 1, 20))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.list(new ShipmentQuery(null, null, null, null, null, false), 0, 20))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(repository);
    }

    private Shipment existing(String status) {
        var content = ShipmentContent.from(LocalDate.of(2026, 9, 18), form(List.of("小周"), null, null),
                status, "陈小鱼", "", "");
        return new Shipment(1L, "FH20260918-1", content, 2, "employee:8", "employee:8",
                java.time.LocalDateTime.of(2026, 9, 18, 9, 12), java.time.LocalDateTime.of(2026, 9, 18, 9, 12));
    }

    private ShipmentFormInput form(List<String> preparers, BigDecimal weight, BigDecimal freight) {
        return new ShipmentFormInput("淘宝", "贝贝鱼淘宝旗舰店", preparers, "张三", "13800138000",
                "浙江省", "杭州市", "余杭区", "示例路1号", "蓝色收纳盒 × 2\n纸箱 × 1", "轻放", freight,
                new AneOrderDraft("水族用品", "纸箱", weight, new BigDecimal("0.12"), 2, 524, 180, 104, "外箱加固"), 1L, 1L);
    }
}
