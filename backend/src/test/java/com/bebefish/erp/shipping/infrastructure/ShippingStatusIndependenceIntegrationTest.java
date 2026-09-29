package com.bebefish.erp.shipping.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.bebefish.erp.shipping.domain.ShipmentQuery;
import com.bebefish.erp.shipping.domain.ShipmentRepository;
import com.bebefish.erp.shipping.logistics.AneOrderResult;
import com.bebefish.erp.shipping.logistics.AneOrderStore;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ShippingStatusIndependenceIntegrationTest {
    @Autowired
    private AneOrderStore orderStore;

    @Autowired
    private ShipmentRepository shipments;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void successfulAneOrderKeepsPreparationStatusAndAppearsInShipmentList() {
        var suffix = String.valueOf(System.nanoTime());
        var shipmentNo = "FH-STATUS-" + suffix;
        jdbc.update("""
                insert into shipment
                    (shipment_no, shipment_date, recipient_name, recipient_phone, recipient_address,
                     preparation_content, remark, status, preparer, preparers_json, orderer,
                     version_no, created_by, updated_by, created_at, updated_at)
                values (?, current_date, '收件人', '13800138000', '浙江省杭州市示例路1号',
                        '测试商品1件', '', 'unfinished', '备货员', json_array('备货员'), '下单员',
                        0, 'test', 'test', now(3), now(3))
                """, shipmentNo);
        var shipmentId = jdbc.queryForObject(
                "select id from shipment where shipment_no = ?", Long.class, shipmentNo);
        jdbc.update("""
                insert into shipment_logistics_order
                    (shipment_id, order_no, state, test_environment, tracking_no, child_tracking_nos,
                     message, request_snapshot, operator_id, created_at, updated_at)
                values (?, ?, 'processing', false, '', '', '提交中', '{}', 'test', now(3), now(3))
                """, shipmentId, "BF" + suffix);

        orderStore.finish(shipmentId, new AneOrderResult("succeeded", "ANE" + suffix, "", "下单成功"), "test");

        assertThat(jdbc.queryForMap(
                "select status, tracking_no from shipment where id = ?", shipmentId))
                .containsAllEntriesOf(Map.of("status", "unfinished", "tracking_no", "ANE" + suffix));
        var listed = shipments.findAll(
                new ShipmentQuery(shipmentNo, "", null, null, "", false), PageRequest.of(0, 20))
                .getContent().getFirst();
        assertThat(listed.logisticsOrderState()).isEqualTo("succeeded");
    }
}
