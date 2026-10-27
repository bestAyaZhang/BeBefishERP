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
    public record CancellationClaim(LogisticsOrder order, boolean claimed) {}

    public ShipmentContent cancellationSnapshot(long id) {
        var snapshots = jdbc.queryForList("select request_snapshot from shipment_logistics_order where shipment_id=:id", Map.of("id", id), String.class);
        if (snapshots.isEmpty()) throw new BusinessException("LOGISTICS_ORDER_NOT_FOUND", HttpStatus.NOT_FOUND, "尚未创建安能订单");
        try { return mapper.readValue(snapshots.getFirst(), ShipmentContent.class); }
        catch (JsonProcessingException failure) {
            throw new BusinessException("LOGISTICS_SNAPSHOT_INVALID", HttpStatus.CONFLICT, "原下单资料无法读取，请联系安能网点处理取消");
        }
    }

    @Transactional
    public CancellationClaim claimCancellation(long id, long expectedVersion, String operator) {
        return claimCancellation(id,expectedVersion,operator,config);
    }
    @Transactional
    public CancellationClaim claimCancellation(long id, long expectedVersion, String operator, AneProperties operationConfig) {
        return claimCancellation(id,expectedVersion,operator,operationConfig.testEnvironment());
    }
    @Transactional
    public CancellationClaim claimCancellation(long id,long expectedVersion,String operator,boolean testEnvironment) {
        var ids = jdbc.queryForList("select id from shipment where id=:id for update", Map.of("id", id), Long.class);
        if (ids.isEmpty()) throw new BusinessException("SHIPMENT_NOT_FOUND", HttpStatus.NOT_FOUND, "发货单不存在");
        var order = find(id).orElseThrow(() -> new BusinessException("LOGISTICS_ORDER_NOT_FOUND", HttpStatus.NOT_FOUND, "尚未创建安能订单"));
        if (order.testEnvironment() != testEnvironment)
            throw new BusinessException("LOGISTICS_ENVIRONMENT_CHANGED", HttpStatus.CONFLICT, "当前物流环境与原订单不一致，不能跨环境取消订单");
        if (java.util.Set.of("cancelled", "cancel_processing", "cancel_unknown").contains(order.state()))
            return new CancellationClaim(order, false);
        if (!java.util.Set.of("succeeded", "cancel_rejected").contains(order.state()))
            throw new BusinessException("LOGISTICS_ORDER_NOT_CANCELLABLE", HttpStatus.CONFLICT, "仅能取消已确认下单成功的订单，请先联系物流网点核实原订单");
        if (shipments.findById(id).orElseThrow().version() != expectedVersion)
            throw new BusinessException("SHIPMENT_VERSION_CONFLICT", HttpStatus.CONFLICT, "发货单已更新，请刷新后再取消");
        var params = new MapSqlParameterSource("id", id).addValue("operator", operator);
        jdbc.update("""
                update shipment_logistics_order set state='cancel_processing', message='正在请求物流取消订单，请勿重复操作',
                    operator_id=:operator, updated_at=now(3) where shipment_id=:id
                """, params);
        jdbc.update("update shipment set version_no=version_no+1, updated_by=:operator, updated_at=now(3) where id=:id", params);
        return new CancellationClaim(find(id).orElseThrow(), true);
    }

    @Transactional
    public LogisticsOrder finishCancellation(long id, AneCancellationResult result, String operator) {
        var params = new MapSqlParameterSource("id", id).addValue("state", result.state())
                .addValue("message", result.message()).addValue("operator", operator);
        jdbc.queryForList("select id from shipment where id=:id for update", params, Long.class);
        int changed = jdbc.update("""
                update shipment_logistics_order set state=:state, message=:message, updated_at=now(3)
                where shipment_id=:id and state='cancel_processing'
                """, params);
        if (changed == 1) {
            jdbc.update("""
                    update shipment set tracking_no=case when :state='cancelled' then '' else tracking_no end,
                        version_no=version_no+1, updated_by=:operator, updated_at=now(3) where id=:id
                    """, params);
        }
        return find(id).orElseThrow();
    }

    // Commit a durable reservation before making any remote request; the network call is outside this transaction.
    @Transactional
    public Claim claim(long id, long expectedVersion, String operator) {
        return claim(id,expectedVersion,operator,config);
    }
    @Transactional
    public Claim claim(long id,long expectedVersion,String operator,AneProperties operationConfig) {
        return claim(id,expectedVersion,operator,operationConfig,false);
    }
    @Transactional
    public Claim claim(long id,long expectedVersion,String operator,AneProperties operationConfig,boolean managedCarrier) {
        return claim(id,expectedVersion,operator,operationConfig.testEnvironment(),managedCarrier,"ANE");
    }
    @Transactional
    public Claim claim(long id,long expectedVersion,String operator,boolean testEnvironment,String provider) {
        if(!java.util.Set.of("ANE","YIMI").contains(provider))throw new IllegalArgumentException("Unsupported carrier");
        return claim(id,expectedVersion,operator,testEnvironment,true,provider);
    }
    private Claim claim(long id,long expectedVersion,String operator,boolean testEnvironment,boolean managedCarrier,String provider) {
        var ids = jdbc.queryForList("select id from shipment where id=:id for update", Map.of("id", id), Long.class);
        if (ids.isEmpty()) throw new BusinessException("SHIPMENT_NOT_FOUND", HttpStatus.NOT_FOUND, "发货单不存在");
        var shipment = shipments.findById(id).orElseThrow();
        var existing = find(id);
        if (existing.isPresent() && !"rejected".equals(existing.get().state())) return new Claim(shipment, existing.get().orderNo(), existing.get());
        if (existing.isPresent() && existing.get().testEnvironment() != testEnvironment)
            throw new BusinessException("LOGISTICS_ENVIRONMENT_CHANGED", HttpStatus.CONFLICT, "此发货单已在其他物流环境尝试下单，请新建发货单后再提交");
        if (shipment.version() != expectedVersion) throw new BusinessException("SHIPMENT_VERSION_CONFLICT", HttpStatus.CONFLICT, "发货单已更新，请重新打开下单窗口");
        var content = shipment.content();
        if (!content.trackingNo().isBlank()) throw new BusinessException("SHIPMENT_ALREADY_TRACKED", HttpStatus.CONFLICT, "已填写物流单号，请先核实已有运单，不能重复下单");
        if (provider.equals("ANE") && (content.recipientName().length() > 30 || content.recipientPhone().length() > 30))
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "安能收件人姓名和电话不能超过30字");
        if (!content.logisticsCompany().isBlank() && !content.logisticsCompany().contains("安能") && !managedCarrier)
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "当前物流公司不是安能，请先修改发货单的物流公司");
        String orderNo = existing.map(LogisticsOrder::orderNo).orElseGet(() -> "BF" + UUID.nameUUIDFromBytes(shipment.shipmentNo().getBytes(StandardCharsets.UTF_8)).toString().replace("-", "").substring(0, 28));
        String snapshot;
        try { snapshot = mapper.writeValueAsString(content); }
        catch (JsonProcessingException failure) { throw new IllegalStateException("无法保存物流下单资料", failure); }
        var params = new MapSqlParameterSource("id", id).addValue("orderNo", orderNo).addValue("snapshot", snapshot).addValue("operator", operator).addValue("testEnvironment", testEnvironment).addValue("provider",provider);
        jdbc.update("""
                insert into shipment_logistics_order
                    (shipment_id,order_no,state,test_environment,tracking_no,child_tracking_nos,message,request_snapshot,operator_id,created_at,updated_at,carrier_provider)
                values (:id,:orderNo,'processing',:testEnvironment,'','','正在提交物流下单请求，请勿重复操作',:snapshot,:operator,now(3),now(3),:provider)
                on duplicate key update state='processing', message='正在提交物流下单请求，请勿重复操作',
                    request_snapshot=:snapshot, carrier_provider=:provider, carrier_business_snapshot=null,carrier_response_metadata=null,
                    operator_id=:operator, updated_at=now(3)
                """, params);
        jdbc.update("update shipment set version_no=version_no+1, updated_by=:operator, updated_at=now(3) where id=:id", params);
        return new Claim(shipment, orderNo, null);
    }

    @Transactional
    public LogisticsOrder finish(long id, AneOrderResult result, String operator) {
        return finish(id,result,operator,"安能物流");
    }
    @Transactional
    public LogisticsOrder finish(long id,AneOrderResult result,String operator,String companyName) {
        return finish(id,result,operator,companyName,null);
    }
    @Transactional
    public LogisticsOrder finish(long id,AneOrderResult result,String operator,String companyName,Map<String,Object> metadata) {
        var params = new MapSqlParameterSource("id", id).addValue("state", result.state()).addValue("tracking", result.trackingNo())
                .addValue("children", result.childTrackingNos()).addValue("message", result.message()).addValue("operator", operator).addValue("companyName",companyName)
                .addValue("metadata",json(metadata));
        // Keep the lock order consistent with claim/update: shipment first, logistics order second.
        jdbc.queryForList("select id from shipment where id=:id for update", params, Long.class);
        var shipment = shipments.findById(id).orElseThrow(() ->
                new BusinessException("SHIPMENT_NOT_FOUND", HttpStatus.NOT_FOUND, "发货单不存在"));
        params.addValue("weight", shipment.content().orderDraft().weight())
                .addValue("address", shipment.content().recipientFullAddress());
        int changed = jdbc.update("""
                update shipment_logistics_order set state=:state, tracking_no=:tracking, child_tracking_nos=:children,
                    message=:message,carrier_response_metadata=cast(:metadata as json), updated_at=now(3) where shipment_id=:id and state='processing'
                """, params);
        if (changed == 1 && "succeeded".equals(result.state())) {
            jdbc.update("""
                    update shipment set tracking_no=:tracking, logistics_company=:companyName, weight=:weight,
                        recipient_address=:address, version_no=version_no+1,
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
                state = "unknown"; message = "下单结果待核实，请联系物流网点核对订单号，勿重复下单";
            }
            if ("cancel_processing".equals(state) && updated.isBefore(LocalDateTime.now().minusMinutes(2))) {
                state = "cancel_unknown"; message = AneCancellationResult.unknown().message();
            }
            String provider=rs.getString("carrier_provider");
            return new LogisticsOrder(id, rs.getString("order_no"), state, rs.getString("tracking_no"), rs.getString("child_tracking_nos"), message, updated, rs.getBoolean("test_environment"),provider==null?"ANE":provider);
        }).stream().findFirst();
    }
    private String json(Map<String,Object> value) {
        if(value==null)return null;
        try{return mapper.writeValueAsString(value);}catch(JsonProcessingException error){throw new IllegalStateException("无法保存物流结果",error);}
    }
}
