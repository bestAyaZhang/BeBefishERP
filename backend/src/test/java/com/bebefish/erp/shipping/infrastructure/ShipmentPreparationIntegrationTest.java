package com.bebefish.erp.shipping.infrastructure;

import com.bebefish.erp.common.security.ErpPrincipal;
import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.platform.api.PlatformDtos;
import com.bebefish.erp.platform.application.PlatformCatalogService;
import com.bebefish.erp.shipping.application.ShipmentService;
import com.bebefish.erp.shipping.domain.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ShipmentPreparationIntegrationTest {
    @Autowired ShipmentService shipments;
    @Autowired PlatformCatalogService catalog;
    @Autowired JdbcTemplate jdbc;

    @Test
    void persistsFeedbackWithoutChangingTheSubmittedWeightAndKeepsItAcrossGeneralEdits() {
        long employeeId = employee("备货测试员");
        var created = shipments.create(form("备货测试员"), List.of(employeeId), "test", "录入员");
        // Simulate an already submitted carrier order locally. No external carrier request is sent.
        jdbc.update("insert into shipment_logistics_order (shipment_id,order_no,state,test_environment,request_snapshot,tracking_no,child_tracking_nos,message,operator_id,created_at,updated_at) values (?,?,'succeeded',true,'{}','710TEST','','成功','test',now(3),now(3))", created.id(), "PREP-" + created.id());
        var operator = new ErpPrincipal(employeeId, null, "备货测试员", List.of(), List.of("shipping:view", "shipping:prepare"));
        var updated = shipments.updatePreparation(created.id(), "partially_shipped", new BigDecimal("19.235"), created.version(), operator);
        var reloaded = shipments.get(created.id());
        assertThat(reloaded.preparerEmployeeIds()).containsExactly(employeeId);
        assertThat(reloaded.preparation().actualWeight()).isEqualByComparingTo("19.235");
        assertThat(reloaded.preparation().employeeId()).isEqualTo(employeeId);
        assertThat(reloaded.preparation().updatedBy()).isEqualTo("备货测试员");
        assertThat(reloaded.preparation().updatedAt()).isNotNull();
        assertThat(reloaded.content().status()).isEqualTo("partially_shipped");
        assertThat(reloaded.content().orderDraft().weight()).isEqualByComparingTo("18.5");
        assertThat(reloaded.logisticsOrderState()).isEqualTo("succeeded");
        assertThat(jdbc.queryForObject("select request_snapshot from shipment_logistics_order where shipment_id=?", String.class, created.id())).isEqualTo("{}");
        assertThat(shipments.list(new ShipmentQuery(null, "partially_shipped", null, null, null, false), 1, 100).getContent())
                .anySatisfy(row -> { assertThat(row.id()).isEqualTo(created.id()); assertThat(row.preparation().actualWeight()).isEqualByComparingTo("19.235"); });
        shipments.update(created.id(), updated.content().form(), "completed", updated.version(), "editor");
        assertThat(shipments.get(created.id()).preparation()).isEqualTo(reloaded.preparation());
        assertThatThrownBy(() -> shipments.updatePreparation(created.id(), "completed", BigDecimal.ONE, created.version(), operator))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("SHIPMENT_VERSION_CONFLICT"));
    }

    @Test
    void legacyNameRequiresAdministratorConfirmationAndDuplicateNameDoesNotGrantAccess() {
        long ownerId = employee("同名备货员"), otherId = employee("同名备货员");
        var created = shipments.create(form("同名备货员"), "test", "录入员");
        var owner = new ErpPrincipal(ownerId, null, "同名备货员", List.of(), List.of("shipping:view", "shipping:prepare"));
        assertThatThrownBy(() -> shipments.updatePreparation(created.id(), "completed", null, 0, owner))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("SHIPMENT_PREPARATION_FORBIDDEN"));
        assertThatThrownBy(() -> shipments.update(created.id(), created.content().form(), "unfinished", 0, "test", List.of(ownerId), false))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("SHIPMENT_PREPARER_LINK_FORBIDDEN"));
        var linked = shipments.update(created.id(), created.content().form(), "unfinished", 0, "admin", List.of(ownerId), true);
        var other = new ErpPrincipal(otherId, null, "同名备货员", List.of(), List.of("shipping:view", "shipping:prepare"));
        assertThatThrownBy(() -> shipments.updatePreparation(created.id(), "completed", null, linked.version(), other))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("SHIPMENT_PREPARATION_FORBIDDEN"));
        shipments.updatePreparation(created.id(), "out_of_stock", null, linked.version(), owner);
        assertThat(shipments.get(created.id()).content().status()).isEqualTo("out_of_stock");
        assertThat(jdbc.queryForObject("select count(*) from sys_role_permission rp join sys_role r on r.id=rp.role_id join sys_permission p on p.id=rp.permission_id where r.code='SUPER_ADMIN' and p.code='shipping:prepare'", Integer.class)).isEqualTo(1);
    }

    private long employee(String name) {
        String no = "PREP-" + java.util.UUID.randomUUID();
        jdbc.update("insert into employee (employee_no,name,employment_type,status,source,created_at,updated_at) values (?,?,'formal','active','manual',now(3),now(3))", no, name);
        return jdbc.queryForObject("select id from employee where employee_no=?", Long.class, no);
    }
    private ShipmentFormInput form(String name) {
        var platform = catalog.createPlatform(new PlatformDtos.Create(null, "备货测试平台" + System.nanoTime(), 0, "", null), "test");
        var shop = catalog.createShop(new PlatformDtos.Create(null, "备货测试店", 0, "", platform.id(), "ecommerce", "运营", null), "test");
        return new ShipmentFormInput("淘宝", "测试店铺", List.of(name), "收件人", "13800138000", "浙江省", "杭州市", "余杭区", "测试路1号", "测试商品2件", "", null,
                new AneOrderDraft("测试商品", "纸箱", new BigDecimal("18.5"), new BigDecimal("0.1"), 1, 524, 180, 102, ""),
                "发货人", "13900000000", "浙江省", "金华市", "东阳市", "测试路2号")
                .withSource(new ShipmentSource(platform.id(), shop.id(), platform.name(), shop.name()));
    }
}
