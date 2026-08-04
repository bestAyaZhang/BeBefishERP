package com.bebefish.erp.product.infrastructure;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.product.domain.SupplierQuote;
import com.bebefish.erp.product.domain.SupplierQuoteRepository;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class SupplierQuoteJpaAdapter implements SupplierQuoteRepository {
    private final JdbcTemplate jdbc;

    public SupplierQuoteJpaAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existsBySkuIdAndSupplierId(long skuId, long supplierId, Long excludedQuoteId) {
        var count = excludedQuoteId == null
                ? jdbc.queryForObject(
                        "select count(*) from sku_supplier_quote where sku_id = ? and supplier_id = ?",
                        Long.class, skuId, supplierId
                )
                : jdbc.queryForObject(
                        "select count(*) from sku_supplier_quote where sku_id = ? and supplier_id = ? and id <> ?",
                        Long.class, skuId, supplierId, excludedQuoteId
                );
        return count != null && count > 0;
    }

    @Override
    public SupplierQuote save(SupplierQuote quote) {
        if (quote.id() == null) {
            var id = insertAndReturnId(
                    "insert into sku_supplier_quote (sku_id, supplier_id, supplier_item_no, purchase_price, "
                            + "min_purchase_quantity, is_default, status, created_at, updated_at) "
                            + "values (?, ?, ?, ?, ?, ?, ?, now(3), now(3))",
                    quote.skuId(), quote.supplierId(), quote.supplierItemNo(), quote.purchasePrice(),
                    quote.minPurchaseQuantity(), quote.isDefault(), quote.status()
            );
            return new SupplierQuote(
                    id, quote.skuId(), quote.supplierId(), quote.supplierItemNo(), quote.purchasePrice(),
                    quote.minPurchaseQuantity(), quote.isDefault(), quote.status()
            );
        }
        var updated = jdbc.update(
                "update sku_supplier_quote set supplier_id=?, supplier_item_no=?, purchase_price=?, "
                        + "min_purchase_quantity=?, is_default=?, status=?, updated_at=now(3) where id=? and sku_id=?",
                quote.supplierId(), quote.supplierItemNo(), quote.purchasePrice(), quote.minPurchaseQuantity(),
                quote.isDefault(), quote.status(), quote.id(), quote.skuId()
        );
        if (updated != 1) {
            throw new BusinessException("SUPPLIER_QUOTE_NOT_FOUND", HttpStatus.NOT_FOUND, "供应商报价不存在");
        }
        return quote;
    }

    @Override
    public Optional<SupplierQuote> findById(long id) {
        var quotes = jdbc.query(
                "select id, sku_id, supplier_id, supplier_item_no, purchase_price, min_purchase_quantity, "
                        + "is_default, status from sku_supplier_quote where id = ?",
                (resultSet, rowNumber) -> new SupplierQuote(
                        resultSet.getLong("id"), resultSet.getLong("sku_id"), resultSet.getLong("supplier_id"),
                        resultSet.getString("supplier_item_no"), resultSet.getBigDecimal("purchase_price"),
                        resultSet.getBigDecimal("min_purchase_quantity"), resultSet.getBoolean("is_default"),
                        resultSet.getString("status")
                ), id
        );
        return quotes.stream().findFirst();
    }

    @Override
    public List<SupplierQuote> findBySkuId(long skuId) {
        return jdbc.query(
                "select id, sku_id, supplier_id, supplier_item_no, purchase_price, min_purchase_quantity, "
                        + "is_default, status from sku_supplier_quote where sku_id = ? "
                        + "order by is_default desc, id asc",
                (resultSet, rowNumber) -> new SupplierQuote(
                        resultSet.getLong("id"), resultSet.getLong("sku_id"), resultSet.getLong("supplier_id"),
                        resultSet.getString("supplier_item_no"), resultSet.getBigDecimal("purchase_price"),
                        resultSet.getBigDecimal("min_purchase_quantity"), resultSet.getBoolean("is_default"),
                        resultSet.getString("status")
                ), skuId
        );
    }

    @Override
    public void clearDefault(long skuId) {
        jdbc.update(
                "update sku_supplier_quote set is_default = false, updated_at = now(3) "
                        + "where sku_id = ? and is_default = true",
                skuId
        );
    }

    private long insertAndReturnId(String sql, Object... parameters) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (var index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            return statement;
        }, keyHolder);
        var key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("未返回数据库主键");
        }
        return key.longValue();
    }
}
