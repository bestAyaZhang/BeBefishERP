package com.bebefish.erp.shipping.infrastructure;

import com.bebefish.erp.shipping.domain.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
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
                insert into shipment (shipment_no, shipment_date, recipient_name, recipient_phone,
                    recipient_province, recipient_city, recipient_county, recipient_detail_address, recipient_address,
                    logistics_company, weight, volume, piece_amount, product_type_id, goods_type, pay_type,
                    tracking_no, platform, shop_name, estimated_freight, cargo_name, pack_type, logistics_remark,
                    preparation_content, remark, status, preparer, preparers_json, orderer, version_no,
                    created_by, updated_by, created_at, updated_at)
                values (:shipmentNo, :shipmentDate, :recipientName, :recipientPhone,
                    :recipientProvince, :recipientCity, :recipientCounty, :recipientDetailAddress, :recipientAddress,
                    :logisticsCompany, :weight, :volume, :pieceAmount, :productTypeId, :goodsType, :payType,
                    :trackingNo, :platform, :shopName, :estimatedFreight, :cargoName, :packType, :logisticsRemark,
                    :preparationContent, :remark, :status, :preparer, cast(:preparersJson as json), :orderer, :version,
                    :createdBy, :updatedBy, :createdAt, :updatedAt)
                """, parameters(shipment), keys, new String[]{"id"});
        if (keys.getKey() == null) throw new IllegalStateException("发货单保存后未返回编号");
        return new Shipment(keys.getKey().longValue(), shipment.shipmentNo(), shipment.content(), shipment.version(),
                shipment.createdBy(), shipment.updatedBy(), shipment.createdAt(), shipment.updatedAt());
    }

    @Override
    public boolean update(Shipment shipment, long expectedVersion) {
        return jdbc.update("""
                update shipment set recipient_name=:recipientName, recipient_phone=:recipientPhone,
                    recipient_province=:recipientProvince, recipient_city=:recipientCity, recipient_county=:recipientCounty,
                    recipient_detail_address=:recipientDetailAddress, recipient_address=:recipientAddress,
                    platform=:platform, shop_name=:shopName, preparer=:preparer,
                    preparers_json=cast(:preparersJson as json), estimated_freight=:estimatedFreight,
                    cargo_name=:cargoName, pack_type=:packType, weight=:weight, volume=:volume,
                    piece_amount=:pieceAmount, product_type_id=:productTypeId, goods_type=:goodsType,
                    pay_type=:payType, logistics_remark=:logisticsRemark,
                    preparation_content=:preparationContent, remark=:remark, status=:status,
                    orderer=:orderer, version_no=:version, updated_by=:updatedBy, updated_at=:updatedAt
                where id=:id and version_no=:expectedVersion
                    and (not exists (select 1 from shipment_logistics_order o where o.shipment_id=:id and o.state <> 'rejected')
                        or (recipient_name=:recipientName and recipient_phone=:recipientPhone
                            and recipient_province=:recipientProvince and recipient_city=:recipientCity
                            and recipient_county=:recipientCounty and recipient_detail_address=:recipientDetailAddress
                            and recipient_address=:recipientAddress and platform=:platform and shop_name=:shopName
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
        return jdbc.query("select * from shipment where id=:id", new MapSqlParameterSource("id", id), this::map)
                .stream().findFirst();
    }

    @Override
    public Page<Shipment> findAll(ShipmentQuery query, Pageable pageable) {
        var where = new StringBuilder(" where 1=1");
        var params = new MapSqlParameterSource();
        if (query.keyword() != null && !query.keyword().isBlank()) {
            where.append(" and (shipment_no like :keyword escape '!' or recipient_name like :keyword escape '!'"
                    + " or recipient_phone like :keyword escape '!' or tracking_no like :keyword escape '!'"
                    + " or preparation_content like :keyword escape '!' or preparer like :keyword escape '!'"
                    + " or orderer like :keyword escape '!' or shop_name like :keyword escape '!')");
            params.addValue("keyword", "%" + query.keyword().strip().replace("!", "!!")
                    .replace("%", "!%").replace("_", "!_") + "%");
        }
        if (query.status() != null && !query.status().isBlank()) {
            where.append(" and status=:status"); params.addValue("status", query.status());
        }
        if (query.incompleteOnly()) where.append(" and status <> 'completed'");
        if (query.dateFrom() != null) { where.append(" and shipment_date >= :dateFrom"); params.addValue("dateFrom", query.dateFrom()); }
        if (query.dateTo() != null) { where.append(" and shipment_date <= :dateTo"); params.addValue("dateTo", query.dateTo()); }
        if (query.platform() != null && !query.platform().isBlank()) {
            where.append(" and platform=:platform"); params.addValue("platform", query.platform().strip());
        }
        Long total = jdbc.queryForObject("select count(*) from shipment" + where, params, Long.class);
        params.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        var records = jdbc.query("select * from shipment" + where
                + " order by shipment_date desc, id desc limit :limit offset :offset", params, this::map);
        return new PageImpl<>(records, pageable, total == null ? 0 : total);
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
                .addValue("shipmentDate", c.shipmentDate()).addValue("recipientName", c.recipientName())
                .addValue("recipientPhone", c.recipientPhone()).addValue("recipientProvince", c.recipientProvince())
                .addValue("recipientCity", c.recipientCity()).addValue("recipientCounty", c.recipientCounty())
                .addValue("recipientDetailAddress", c.recipientDetailAddress()).addValue("recipientAddress", c.recipientFullAddress())
                .addValue("logisticsCompany", c.logisticsCompany()).addValue("weight", draft.weight())
                .addValue("volume", draft.volume()).addValue("pieceAmount", draft.pieceAmount())
                .addValue("productTypeId", draft.productTypeId()).addValue("goodsType", draft.goodsType())
                .addValue("payType", draft.payType()).addValue("trackingNo", c.trackingNo())
                .addValue("platform", c.platform()).addValue("shopName", c.shopName())
                .addValue("estimatedFreight", c.estimatedFreight()).addValue("cargoName", draft.cargoName())
                .addValue("packType", draft.packType()).addValue("logisticsRemark", draft.logisticsRemark())
                .addValue("preparationContent", c.preparationContent()).addValue("remark", c.remark())
                .addValue("status", c.status()).addValue("preparer", String.join("、", c.preparers()))
                .addValue("preparersJson", writePreparers(c.preparers())).addValue("orderer", c.orderer())
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
                rs.getString("tracking_no"));
        return new Shipment(rs.getLong("id"), rs.getString("shipment_no"), content, rs.getLong("version_no"),
                rs.getString("created_by"), rs.getString("updated_by"), rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime());
    }

    private Integer getInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private String writePreparers(List<String> preparers) {
        try { return mapper.writeValueAsString(preparers); }
        catch (JsonProcessingException failure) { throw new IllegalStateException("无法保存备货人", failure); }
    }

    private List<String> readPreparers(String json) throws SQLException {
        try { return json == null || json.isBlank() ? List.of() : mapper.readValue(json, new TypeReference<>() {}); }
        catch (JsonProcessingException failure) { throw new SQLException("无法读取备货人", failure); }
    }
}
