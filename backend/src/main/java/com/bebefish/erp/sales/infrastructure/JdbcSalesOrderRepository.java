package com.bebefish.erp.sales.infrastructure;

import com.bebefish.erp.sales.domain.SalesOrder;
import com.bebefish.erp.sales.domain.SalesOrderItem;
import com.bebefish.erp.sales.domain.SalesOrderRepository;
import com.bebefish.erp.sales.domain.SalesOrderSearchCriteria;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSalesOrderRepository implements SalesOrderRepository {
    private static final String ORDER_COLUMNS = "id, sales_no, customer_id, customer_name, warehouse_id, "
            + "warehouse_name, sales_date, salesperson_mobile, status, transport_method, settlement_cycle, "
            + "payment_method, delivery_address, logistics_company, tracking_no, package_note, invoice_required, "
            + "invoice_status, goods_amount, discount_amount, shipping_fee, total_amount, received_amount, "
            + "outstanding_amount, remark, created_at, updated_at";
    private static final String ITEM_COLUMNS = "id, sku_id, quantity, default_unit_price, unit_price, "
            + "discount_rate, amount, standard_cost_snapshot, item_no_snapshot, product_name_snapshot, "
            + "sku_code_snapshot, sku_name_snapshot, specification_snapshot, packaging_snapshot, "
            + "carton_quantity_snapshot, barcode_snapshot, sales_unit_snapshot";
    private final JdbcTemplate jdbc;

    public JdbcSalesOrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SalesOrder save(SalesOrder order) {
        var now = LocalDateTime.now();
        if (order.id() == null) {
            var id = insertOrder(order, now);
            replaceItems(id, order.items());
            return order.withIdentity(id, now, now);
        }
        var updated = jdbc.update(
                "update sales_order set sales_no = ?, customer_id = ?, customer_name = ?, warehouse_id = ?, "
                        + "warehouse_name = ?, sales_date = ?, salesperson_mobile = ?, status = ?, "
                        + "transport_method = ?, settlement_cycle = ?, payment_method = ?, delivery_address = ?, "
                        + "logistics_company = ?, tracking_no = ?, package_note = ?, invoice_required = ?, "
                        + "invoice_status = ?, goods_amount = ?, discount_amount = ?, shipping_fee = ?, "
                        + "total_amount = ?, received_amount = ?, outstanding_amount = ?, remark = ?, updated_at = ? "
                        + "where id = ?",
                order.salesNo(), order.customerId(), order.customerName(), order.warehouseId(), order.warehouseName(),
                order.salesDate(), order.salespersonMobile(), order.status(), order.transportMethod(),
                order.settlementCycle(), order.paymentMethod(), order.deliveryAddress(), order.logisticsCompany(),
                order.trackingNo(), order.packageNote(), order.invoiceRequired(), order.invoiceStatus(),
                order.goodsAmount(), order.discountAmount(), order.shippingFee(), order.totalAmount(),
                order.receivedAmount(), order.outstandingAmount(), order.remark(), now, order.id()
        );
        if (updated != 1) {
            throw new EmptyResultDataAccessException("销售单不存在", 1);
        }
        replaceItems(order.id(), order.items());
        return order.withIdentity(order.id(), order.createdAt(), now);
    }

    @Override
    public Optional<SalesOrder> findById(long id) {
        var orders = jdbc.query(
                "select " + ORDER_COLUMNS + " from sales_order where id = ?",
                (resultSet, rowNumber) -> mapOrder(resultSet), id
        );
        if (orders.isEmpty()) {
            return Optional.empty();
        }
        var order = orders.getFirst();
        return Optional.of(new SalesOrder(
                order.id(), order.salesNo(), order.customerId(), order.customerName(), order.warehouseId(),
                order.warehouseName(), order.salesDate(), order.salespersonMobile(), order.status(),
                order.transportMethod(), order.settlementCycle(), order.paymentMethod(), order.deliveryAddress(),
                order.logisticsCompany(), order.trackingNo(), order.packageNote(), order.invoiceRequired(),
                order.invoiceStatus(), order.goodsAmount(), order.discountAmount(), order.shippingFee(),
                order.totalAmount(), order.receivedAmount(), order.outstandingAmount(), order.remark(),
                order.createdAt(), order.updatedAt(), findItems(id)
        ));
    }

    @Override
    public Page<SalesOrder> findAll(SalesOrderSearchCriteria criteria, Pageable pageable) {
        var where = new ArrayList<String>();
        var parameters = new ArrayList<Object>();
        if (criteria != null && criteria.keyword() != null && !criteria.keyword().isBlank()) {
            var pattern = "%" + criteria.keyword().trim() + "%";
            where.add("(sales_no like ? or customer_name like ? or warehouse_name like ?)");
            parameters.add(pattern);
            parameters.add(pattern);
            parameters.add(pattern);
        }
        if (criteria != null && criteria.status() != null && !criteria.status().isBlank()) {
            where.add("status = ?");
            parameters.add(criteria.status());
        }
        if (criteria != null && criteria.salesDateFrom() != null) {
            where.add("sales_date >= ?");
            parameters.add(criteria.salesDateFrom());
        }
        if (criteria != null && criteria.salesDateTo() != null) {
            where.add("sales_date <= ?");
            parameters.add(criteria.salesDateTo());
        }
        var whereSql = where.isEmpty() ? "" : " where " + String.join(" and ", where);
        var total = jdbc.queryForObject(
                "select count(*) from sales_order" + whereSql, Long.class, parameters.toArray()
        );
        var pageParameters = new ArrayList<>(parameters);
        pageParameters.add(pageable.getPageSize());
        pageParameters.add(pageable.getOffset());
        var ids = jdbc.queryForList(
                "select id from sales_order" + whereSql
                        + " order by sales_date desc, id desc limit ? offset ?",
                Long.class, pageParameters.toArray()
        );
        var records = ids.stream().map(this::findById).flatMap(Optional::stream).toList();
        return new PageImpl<>(records, pageable, total == null ? 0 : total);
    }

    @Override
    public void deleteById(long id) {
        jdbc.update("delete from sales_order where id = ?", id);
    }

    private long insertOrder(SalesOrder order, LocalDateTime now) {
        var keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "insert into sales_order (sales_no, customer_id, customer_name, warehouse_id, warehouse_name, "
                            + "sales_date, salesperson_mobile, status, transport_method, settlement_cycle, "
                            + "payment_method, delivery_address, logistics_company, tracking_no, package_note, "
                            + "invoice_required, invoice_status, goods_amount, discount_amount, shipping_fee, "
                            + "total_amount, received_amount, outstanding_amount, remark, created_at, updated_at) "
                            + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            setOrderParameters(statement, order, now);
            return statement;
        }, keyHolder);
        if (keyHolder.getKey() == null) {
            throw new IllegalStateException("销售单保存失败");
        }
        return keyHolder.getKey().longValue();
    }

    private void setOrderParameters(PreparedStatement statement, SalesOrder order, LocalDateTime now)
            throws java.sql.SQLException {
        statement.setString(1, order.salesNo());
        statement.setLong(2, order.customerId());
        statement.setString(3, order.customerName());
        statement.setLong(4, order.warehouseId());
        statement.setString(5, order.warehouseName());
        statement.setObject(6, order.salesDate());
        statement.setString(7, order.salespersonMobile());
        statement.setString(8, order.status());
        statement.setString(9, order.transportMethod());
        statement.setString(10, order.settlementCycle());
        setNullableString(statement, 11, order.paymentMethod());
        setNullableString(statement, 12, order.deliveryAddress());
        setNullableString(statement, 13, order.logisticsCompany());
        setNullableString(statement, 14, order.trackingNo());
        setNullableString(statement, 15, order.packageNote());
        statement.setBoolean(16, order.invoiceRequired());
        statement.setString(17, order.invoiceStatus());
        statement.setBigDecimal(18, order.goodsAmount());
        statement.setBigDecimal(19, order.discountAmount());
        statement.setBigDecimal(20, order.shippingFee());
        statement.setBigDecimal(21, order.totalAmount());
        statement.setBigDecimal(22, order.receivedAmount());
        statement.setBigDecimal(23, order.outstandingAmount());
        setNullableString(statement, 24, order.remark());
        statement.setObject(25, order.createdAt() == null ? now : order.createdAt());
        statement.setObject(26, now);
    }

    private void replaceItems(long orderId, List<SalesOrderItem> items) {
        jdbc.update("delete from sales_order_item where sales_order_id = ?", orderId);
        for (var item : items) {
            jdbc.update(
                    "insert into sales_order_item (sales_order_id, sku_id, quantity, default_unit_price, unit_price, "
                            + "discount_rate, amount, standard_cost_snapshot, item_no_snapshot, product_name_snapshot, "
                            + "sku_code_snapshot, sku_name_snapshot, specification_snapshot, packaging_snapshot, "
                            + "carton_quantity_snapshot, barcode_snapshot, sales_unit_snapshot) "
                            + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    orderId, item.skuId(), item.quantity(), item.defaultUnitPrice(), item.unitPrice(),
                    item.discountRate(), item.amount(), item.standardCostSnapshot(), item.itemNoSnapshot(),
                    item.productNameSnapshot(), item.skuCodeSnapshot(), item.skuNameSnapshot(),
                    item.specificationSnapshot(), item.packagingSnapshot(), item.cartonQuantitySnapshot(),
                    item.barcodeSnapshot(), item.salesUnitSnapshot()
            );
        }
    }

    private SalesOrder mapOrder(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new SalesOrder(
                resultSet.getLong("id"), resultSet.getString("sales_no"), resultSet.getLong("customer_id"),
                resultSet.getString("customer_name"), resultSet.getLong("warehouse_id"),
                resultSet.getString("warehouse_name"), resultSet.getObject("sales_date", java.time.LocalDate.class),
                resultSet.getString("salesperson_mobile"), resultSet.getString("status"),
                resultSet.getString("transport_method"), resultSet.getString("settlement_cycle"),
                resultSet.getString("payment_method"), resultSet.getString("delivery_address"),
                resultSet.getString("logistics_company"), resultSet.getString("tracking_no"),
                resultSet.getString("package_note"), resultSet.getBoolean("invoice_required"),
                resultSet.getString("invoice_status"), resultSet.getBigDecimal("goods_amount"),
                resultSet.getBigDecimal("discount_amount"), resultSet.getBigDecimal("shipping_fee"),
                resultSet.getBigDecimal("total_amount"), resultSet.getBigDecimal("received_amount"),
                resultSet.getBigDecimal("outstanding_amount"), resultSet.getString("remark"),
                resultSet.getObject("created_at", java.time.LocalDateTime.class),
                resultSet.getObject("updated_at", java.time.LocalDateTime.class), List.of()
        );
    }

    private List<SalesOrderItem> findItems(long orderId) {
        return jdbc.query(
                "select " + ITEM_COLUMNS + " from sales_order_item where sales_order_id = ? order by id asc",
                (resultSet, rowNumber) -> new SalesOrderItem(
                        resultSet.getLong("id"), resultSet.getLong("sku_id"), resultSet.getBigDecimal("quantity"),
                        resultSet.getBigDecimal("default_unit_price"), resultSet.getBigDecimal("unit_price"),
                        resultSet.getBigDecimal("discount_rate"), resultSet.getBigDecimal("amount"),
                        resultSet.getBigDecimal("standard_cost_snapshot"), resultSet.getString("item_no_snapshot"),
                        resultSet.getString("product_name_snapshot"), resultSet.getString("sku_code_snapshot"),
                        resultSet.getString("sku_name_snapshot"), resultSet.getString("specification_snapshot"),
                        resultSet.getString("packaging_snapshot"), getNullableInteger(resultSet, "carton_quantity_snapshot"),
                        resultSet.getString("barcode_snapshot"), resultSet.getString("sales_unit_snapshot")
                ), orderId
        );
    }

    private void setNullableString(PreparedStatement statement, int index, String value) throws java.sql.SQLException {
        if (value == null || value.isBlank()) statement.setNull(index, Types.VARCHAR);
        else statement.setString(index, value);
    }

    private Integer getNullableInteger(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        var value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }
}
