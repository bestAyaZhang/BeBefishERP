package com.bebefish.erp.shipping.infrastructure;

import com.bebefish.erp.shipping.domain.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcShipmentRepository implements ShipmentRepository {
    private static final String SHIPMENT_SELECT = """
            select s.*,
                   case when o.state='processing' and o.updated_at < now(3) - interval 2 minute
                        then 'unknown'
                        when o.state='cancel_processing' and o.updated_at < now(3) - interval 2 minute
                        then 'cancel_unknown' else o.state end as logistics_order_state
            from shipment s
            left join shipment_logistics_order o on o.shipment_id=s.id
            """;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcShipmentRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public Shipment insert(Shipment shipment) {
        var keys = new GeneratedKeyHolder();
        jdbc.update("""
                insert into shipment (shipment_no, shipment_date, sender_name, sender_phone, sender_province,
                    sender_city, sender_county, sender_detail_address, recipient_name, recipient_phone,
                    recipient_province, recipient_city, recipient_county, recipient_detail_address, recipient_address,
                    logistics_company, weight, volume, piece_amount, product_type_id, goods_type, pay_type,
                    tracking_no, platform, shop_name, platform_id, shop_id, estimated_freight, cargo_name, pack_type, logistics_remark,
                    preparation_content, remark, status, preparer, preparers_json, preparer_employee_ids_json,
                    actual_weight, preparation_updated_by, preparation_employee_id, preparation_updated_at, orderer, version_no,
                    created_by, updated_by, created_at, updated_at)
                values (:shipmentNo, :shipmentDate, :senderName, :senderPhone, :senderProvince,
                    :senderCity, :senderCounty, :senderDetailAddress, :recipientName, :recipientPhone,
                    :recipientProvince, :recipientCity, :recipientCounty, :recipientDetailAddress, :recipientAddress,
                    :logisticsCompany, :weight, :volume, :pieceAmount, :productTypeId, :goodsType, :payType,
                    :trackingNo, :platform, :shopName, :platformId, :shopId, :estimatedFreight, :cargoName, :packType, :logisticsRemark,
                    :preparationContent, :remark, :status, :preparer, cast(:preparersJson as json), cast(:preparerEmployeeIdsJson as json),
                    :actualWeight, :preparationUpdatedBy, :preparationEmployeeId, :preparationUpdatedAt, :orderer, :version,
                    :createdBy, :updatedBy, :createdAt, :updatedAt)
                """, parameters(shipment), keys, new String[]{"id"});
        if (keys.getKey() == null) throw new IllegalStateException("发货单保存后未返回编号");
        return new Shipment(keys.getKey().longValue(), shipment.shipmentNo(), shipment.content(), shipment.version(),
                shipment.createdBy(), shipment.updatedBy(), shipment.createdAt(), shipment.updatedAt(),
                shipment.logisticsOrderState(), shipment.preparerEmployeeIds(), shipment.preparation());
    }

    @Override
    public boolean update(Shipment shipment, long expectedVersion) {
        return jdbc.update("""
                update shipment set sender_name=:senderName, sender_phone=:senderPhone,
                    sender_province=:senderProvince, sender_city=:senderCity, sender_county=:senderCounty,
                    sender_detail_address=:senderDetailAddress,
                    recipient_name=:recipientName, recipient_phone=:recipientPhone,
                    recipient_province=:recipientProvince, recipient_city=:recipientCity, recipient_county=:recipientCounty,
                    recipient_detail_address=:recipientDetailAddress, recipient_address=:recipientAddress,
                    platform=:platform, shop_name=:shopName, platform_id=:platformId, shop_id=:shopId, preparer=:preparer,
                    preparers_json=cast(:preparersJson as json), preparer_employee_ids_json=cast(:preparerEmployeeIdsJson as json),
                    actual_weight=:actualWeight, preparation_updated_by=:preparationUpdatedBy,
                    preparation_employee_id=:preparationEmployeeId, preparation_updated_at=:preparationUpdatedAt,
                    estimated_freight=:estimatedFreight,
                    cargo_name=:cargoName, pack_type=:packType, weight=:weight, volume=:volume,
                    piece_amount=:pieceAmount, product_type_id=:productTypeId, goods_type=:goodsType,
                    pay_type=:payType, logistics_remark=:logisticsRemark,
                    preparation_content=:preparationContent, remark=:remark, status=:status,
                    orderer=:orderer, version_no=:version, updated_by=:updatedBy, updated_at=:updatedAt
                where id=:id and version_no=:expectedVersion
                    and (not exists (select 1 from shipment_logistics_order o where o.shipment_id=:id and o.state <> 'rejected')
                        or (sender_name=:senderName and sender_phone=:senderPhone
                            and sender_province=:senderProvince and sender_city=:senderCity
                            and sender_county=:senderCounty and sender_detail_address=:senderDetailAddress
                            and recipient_name=:recipientName and recipient_phone=:recipientPhone
                            and recipient_province=:recipientProvince and recipient_city=:recipientCity
                            and recipient_county=:recipientCounty and recipient_detail_address=:recipientDetailAddress
                            and recipient_address=:recipientAddress and platform=:platform and shop_name=:shopName
                            and (platform_id <=> :platformId) and (shop_id <=> :shopId)
                            and preparer=:preparer and preparers_json=cast(:preparersJson as json)
                            and (estimated_freight <=> :estimatedFreight) and cargo_name=:cargoName
                            and pack_type=:packType and (weight <=> :weight) and (volume <=> :volume)
                            and (piece_amount <=> :pieceAmount) and (product_type_id <=> :productTypeId)
                            and (goods_type <=> :goodsType) and (pay_type <=> :payType)
                            and logistics_remark=:logisticsRemark))
                """, parameters(shipment).addValue("expectedVersion", expectedVersion)) == 1;
    }

    @Override
    public Optional<Shipment> findById(long id) {
        return jdbc.query(SHIPMENT_SELECT + " where s.id=:id", new MapSqlParameterSource("id", id), this::map)
                .stream().findFirst();
    }

    @Override
    public Optional<Shipment> findByIdForUpdate(long id) {
        jdbc.queryForList("select id from shipment where id=:id for update",Map.of("id",id));
        return findById(id);
    }

    @Override
    public Page<Shipment> findAll(ShipmentQuery query, Pageable pageable) {
        var where = new StringBuilder(" where 1=1");
        var params = new MapSqlParameterSource();
        if (query.keyword() != null && !query.keyword().isBlank()) {
            where.append(" and (s.shipment_no like :keyword escape '!' or s.recipient_name like :keyword escape '!'"
                    + " or s.recipient_phone like :keyword escape '!' or s.tracking_no like :keyword escape '!'"
                    + " or s.preparation_content like :keyword escape '!' or s.preparer like :keyword escape '!'"
                    + " or s.orderer like :keyword escape '!' or s.shop_name like :keyword escape '!')");
            params.addValue("keyword", "%" + query.keyword().strip().replace("!", "!!")
                    .replace("%", "!%").replace("_", "!_") + "%");
        }
        if (query.status() != null && !query.status().isBlank()) {
            where.append(" and s.status=:status"); params.addValue("status", query.status());
        }
        if (query.incompleteOnly()) where.append(" and s.status <> 'completed'");
        if (query.dateFrom() != null) { where.append(" and s.shipment_date >= :dateFrom"); params.addValue("dateFrom", query.dateFrom()); }
        if (query.dateTo() != null) { where.append(" and s.shipment_date <= :dateTo"); params.addValue("dateTo", query.dateTo()); }
        if (query.platform() != null && !query.platform().isBlank()) {
            where.append(" and s.platform=:platform"); params.addValue("platform", query.platform().strip());
        }
        if(query.platformId()!=null) { where.append(" and s.platform_id=:platformId"); params.addValue("platformId",query.platformId()); }
        if(query.shopId()!=null) { where.append(" and s.shop_id=:shopId"); params.addValue("shopId",query.shopId()); }
        if(query.unlinkedOnly()) where.append(" and s.shop_id is null");
        Long total = jdbc.queryForObject("select count(*) from shipment s" + where, params, Long.class);
        params.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        var records = jdbc.query(SHIPMENT_SELECT + where
                + " order by s.shipment_date desc, s.id desc limit :limit offset :offset", params, this::map);
        return new PageImpl<>(records, pageable, total == null ? 0 : total);
    }

    @Override
    public ShipmentSummary summary(LocalDate date) {
        return jdbc.queryForObject("""
                select count(*) today_count,
                       coalesce(sum(status = 'unfinished'), 0) unfinished_count,
                       coalesce(sum(status = 'completed'), 0) completed_count,
                       coalesce(sum(status = 'out_of_stock'), 0) out_of_stock_count,
                       coalesce(sum(status = 'partially_shipped'), 0) partially_shipped_count
                from shipment where shipment_date=:date
                """, Map.of("date", date), (rs, row) -> new ShipmentSummary(
                rs.getLong("today_count"), rs.getLong("unfinished_count"), rs.getLong("completed_count"),
                rs.getLong("out_of_stock_count"), rs.getLong("partially_shipped_count")));
    }

    @Override
    public boolean hasNonRejectedLogisticsOrder(long id) {
        Integer count = jdbc.queryForObject("select count(*) from shipment_logistics_order where shipment_id=:id and state <> 'rejected'",
                Map.of("id", id), Integer.class);
        return count != null && count > 0;
    }

    private MapSqlParameterSource parameters(Shipment shipment) {
        var c = shipment.content();
        var draft = c.orderDraft();
        return new MapSqlParameterSource().addValue("id", shipment.id()).addValue("shipmentNo", shipment.shipmentNo())
                .addValue("shipmentDate", c.shipmentDate()).addValue("senderName", c.senderName())
                .addValue("senderPhone", c.senderPhone()).addValue("senderProvince", c.senderProvince())
                .addValue("senderCity", c.senderCity()).addValue("senderCounty", c.senderCounty())
                .addValue("senderDetailAddress", c.senderDetailAddress()).addValue("recipientName", c.recipientName())
                .addValue("recipientPhone", c.recipientPhone()).addValue("recipientProvince", c.recipientProvince())
                .addValue("recipientCity", c.recipientCity()).addValue("recipientCounty", c.recipientCounty())
                .addValue("recipientDetailAddress", c.recipientDetailAddress()).addValue("recipientAddress", c.recipientFullAddress())
                .addValue("logisticsCompany", c.logisticsCompany()).addValue("weight", draft.weight())
                .addValue("volume", draft.volume()).addValue("pieceAmount", draft.pieceAmount())
                .addValue("productTypeId", draft.productTypeId()).addValue("goodsType", draft.goodsType())
                .addValue("payType", draft.payType()).addValue("trackingNo", c.trackingNo())
                .addValue("platform", c.platform()).addValue("shopName", c.shopName())
                .addValue("platformId", c.platformId()).addValue("shopId", c.shopId())
                .addValue("estimatedFreight", c.estimatedFreight()).addValue("cargoName", draft.cargoName())
                .addValue("packType", draft.packType()).addValue("logisticsRemark", draft.logisticsRemark())
                .addValue("preparationContent", c.preparationContent()).addValue("remark", c.remark())
                .addValue("status", c.status()).addValue("preparer", String.join("、", c.preparers()))
                .addValue("preparersJson", writeJson(c.preparers())).addValue("orderer", c.orderer())
                .addValue("preparerEmployeeIdsJson", writeJson(shipment.preparerEmployeeIds()))
                .addValue("actualWeight", shipment.preparation().actualWeight())
                .addValue("preparationUpdatedBy", shipment.preparation().updatedBy())
                .addValue("preparationEmployeeId", shipment.preparation().employeeId())
                .addValue("preparationUpdatedAt", shipment.preparation().updatedAt())
                .addValue("version", shipment.version()).addValue("createdBy", shipment.createdBy())
                .addValue("updatedBy", shipment.updatedBy()).addValue("createdAt", shipment.createdAt())
                .addValue("updatedAt", shipment.updatedAt());
    }

    private Shipment map(ResultSet rs, int row) throws SQLException {
        var draft = new AneOrderDraft(rs.getString("cargo_name"), rs.getString("pack_type"), rs.getBigDecimal("weight"),
                rs.getBigDecimal("volume"), getInteger(rs, "piece_amount"), getInteger(rs, "product_type_id"),
                getInteger(rs, "goods_type"), getInteger(rs, "pay_type"), rs.getString("logistics_remark"));
        var content = new ShipmentContent(rs.getDate("shipment_date").toLocalDate(), rs.getString("platform"),
                rs.getString("shop_name"), readPreparers(rs.getString("preparers_json")), rs.getString("recipient_name"),
                rs.getString("recipient_phone"), rs.getString("recipient_province"), rs.getString("recipient_city"),
                rs.getString("recipient_county"), rs.getString("recipient_detail_address"),
                rs.getString("preparation_content"), rs.getString("remark"), rs.getBigDecimal("estimated_freight"),
                draft, rs.getString("status"), rs.getString("orderer"), rs.getString("logistics_company"),
                rs.getString("tracking_no"), rs.getObject("platform_id",Long.class), rs.getObject("shop_id",Long.class),
                rs.getString("sender_name"), rs.getString("sender_phone"),
                rs.getString("sender_province"), rs.getString("sender_city"), rs.getString("sender_county"),
                rs.getString("sender_detail_address"));
        return new Shipment(rs.getLong("id"), rs.getString("shipment_no"), content, rs.getLong("version_no"),
                rs.getString("created_by"), rs.getString("updated_by"), rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime(), rs.getString("logistics_order_state"),
                readEmployeeIds(rs.getString("preparer_employee_ids_json")), new PreparationProgress(rs.getBigDecimal("actual_weight"),
                rs.getString("preparation_updated_by"), rs.getObject("preparation_employee_id", Long.class),
                rs.getTimestamp("preparation_updated_at") == null ? null : rs.getTimestamp("preparation_updated_at").toLocalDateTime()));
    }

    private Integer getInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private String writeJson(List<?> values) {
        try { return mapper.writeValueAsString(values); }
        catch (JsonProcessingException failure) { throw new IllegalStateException("无法保存备货人", failure); }
    }

    private List<String> readPreparers(String json) throws SQLException {
        try { return json == null || json.isBlank() ? List.of() : mapper.readValue(json, new TypeReference<>() {}); }
        catch (JsonProcessingException failure) { throw new SQLException("无法读取备货人", failure); }
    }

    private List<Long> readEmployeeIds(String json) throws SQLException {
        try { return json == null || json.isBlank() ? List.of() : mapper.readValue(json, new TypeReference<>() {}); }
        catch (JsonProcessingException failure) { throw new SQLException("无法读取备货账号", failure); }
    }
}
