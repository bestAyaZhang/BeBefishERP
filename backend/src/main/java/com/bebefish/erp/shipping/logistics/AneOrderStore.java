package com.bebefish.erp.shipping.logistics;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.shipping.domain.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AneOrderStore {
    private final NamedParameterJdbcTemplate jdbc;
    private final ShipmentRepository shipments;
    private final ObjectMapper mapper;
    private final AneProperties config;
    public AneOrderStore(NamedParameterJdbcTemplate jdbc, ShipmentRepository shipments, ObjectMapper mapper, AneProperties config) {
        this.jdbc = jdbc; this.shipments = shipments; this.mapper = mapper; this.config = config;
    }
    public record Claim(Shipment shipment, String orderNo, LogisticsOrder existing) {}

    // Commit a durable reservation before making any remote request; the network call is outside this transaction.
    @Transactional
    public Claim claim(long id, long expectedVersion, String operator) {
        var ids = jdbc.queryForList("select id from shipment where id=:id for update", Map.of("id", id), Long.class);
        if (ids.isEmpty()) throw new BusinessException("SHIPMENT_NOT_FOUND", HttpStatus.NOT_FOUND, "发货单不存在");
        var shipment = shipments.findById(id).orElseThrow();
        var existing = find(id);
        if (existing.isPresent() && !"rejected".equals(existing.get().state())) return new Claim(shipment, existing.get().orderNo(), existing.get());
        if (existing.isPresent() && existing.get().testEnvironment() != config.testEnvironment())
            throw new BusinessException("LOGISTICS_ENVIRONMENT_CHANGED", HttpStatus.CONFLICT, "此发货单已在其他物流环境尝试下单，请新建发货单后再提交");
        if (shipment.version() != expectedVersion) throw new BusinessException("SHIPMENT_VERSION_CONFLICT", HttpStatus.CONFLICT, "发货单已更新，请重新打开下单窗口");
        var content = shipment.content();
        if (!content.trackingNo().isBlank()) throw new BusinessException("SHIPMENT_ALREADY_TRACKED", HttpStatus.CONFLICT, "已填写物流单号，请先核实已有运单，不能重复下单");
        if (content.recipientName().length() > 30 || content.recipientPhone().length() > 30)
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "安能收件人姓名和电话不能超过30字");
        if (!content.logisticsCompany().isBlank() && !content.logisticsCompany().contains("安能"))
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "当前物流公司不是安能，请先修改发货单的物流公司");
        String orderNo = existing.map(LogisticsOrder::orderNo).orElseGet(() -> "BF" + UUID.nameUUIDFromBytes(shipment.shipmentNo().getBytes(StandardCharsets.UTF_8)).toString().replace("-", "").substring(0, 28));
        String snapshot;
        try { snapshot = mapper.writeValueAsString(content); }
        catch (JsonProcessingException failure) { throw new IllegalStateException("无法保存物流下单资料", failure); }
        var params = new MapSqlParameterSource("id", id).addValue("orderNo", orderNo).addValue("snapshot", snapshot).addValue("operator", operator).addValue("testEnvironment", config.testEnvironment());
        jdbc.update("""
                insert into shipment_logistics_order
                    (shipment_id,order_no,state,test_environment,tracking_no,child_tracking_nos,message,request_snapshot,operator_id,created_at,updated_at)
                values (:id,:orderNo,'processing',:testEnvironment,'','','正在提交安能下单请求，请勿重复操作',:snapshot,:operator,now(3),now(3))
                on duplicate key update state='processing', message='正在提交安能下单请求，请勿重复操作',
                    request_snapshot=:snapshot, operator_id=:operator, updated_at=now(3)
                """, params);
        jdbc.update("update shipment set version_no=version_no+1, updated_by=:operator, updated_at=now(3) where id=:id", params);
        return new Claim(shipment, orderNo, null);
    }

    @Transactional
    public LogisticsOrder finish(long id, AneOrderResult result, String operator) {
        var params = new MapSqlParameterSource("id", id).addValue("state", result.state()).addValue("tracking", result.trackingNo())
                .addValue("children", result.childTrackingNos()).addValue("message", result.message()).addValue("operator", operator);
        // Keep the lock order consistent with claim/update: shipment first, logistics order second.
        jdbc.queryForList("select id from shipment where id=:id for update", params, Long.class);
        var shipment = shipments.findById(id).orElseThrow(() ->
                new BusinessException("SHIPMENT_NOT_FOUND", HttpStatus.NOT_FOUND, "发货单不存在"));
        params.addValue("weight", shipment.content().orderDraft().weight())
                .addValue("address", shipment.content().recipientFullAddress());
        int changed = jdbc.update("""
                update shipment_logistics_order set state=:state, tracking_no=:tracking, child_tracking_nos=:children,
                    message=:message, updated_at=now(3) where shipment_id=:id and state='processing'
                """, params);
        if (changed == 1 && "succeeded".equals(result.state())) {
            jdbc.update("""
                    update shipment set tracking_no=:tracking, logistics_company='安能物流', weight=:weight,
                        recipient_address=:address, status='completed', version_no=version_no+1,
                        updated_by=:operator, updated_at=now(3) where id=:id
                    """, params);
        }
        return find(id).orElseThrow();
    }

    public Optional<LogisticsOrder> find(long id) {
        return jdbc.query("select * from shipment_logistics_order where shipment_id=:id", Map.of("id", id), (rs, row) -> {
            String state = rs.getString("state"), message = rs.getString("message");
            var updated = rs.getTimestamp("updated_at").toLocalDateTime();
            if ("processing".equals(state) && updated.isBefore(LocalDateTime.now().minusMinutes(2))) {
                state = "unknown"; message = "下单结果待核实，请联系安能网点核对订单号，勿重复下单";
            }
            return new LogisticsOrder(id, rs.getString("order_no"), state, rs.getString("tracking_no"), rs.getString("child_tracking_nos"), message, updated, rs.getBoolean("test_environment"));
        }).stream().findFirst();
    }
}
