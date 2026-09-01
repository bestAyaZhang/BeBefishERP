package com.bebefish.erp.product.infrastructure;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.product.domain.Packaging;
import com.bebefish.erp.product.domain.Product;
import com.bebefish.erp.product.domain.ProductRepository;
import com.bebefish.erp.product.domain.ProductType;
import com.bebefish.erp.product.domain.Sku;
import com.bebefish.erp.product.domain.Specification;
import com.bebefish.erp.product.domain.SupplierQuote;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

@Repository
public class ProductJpaAdapter implements ProductRepository {
    private static final Set<String> PRODUCT_DIMENSION_NAMES = Set.of("口径", "高度", "容量", "重量");
    private static final String PRODUCT_COLUMNS = "id, product_code, item_no, product_name, category_id, "
            + "brand, product_type, main_image_file_id, status, remark, created_at, updated_at";
    private final JdbcTemplate jdbc;

    public ProductJpaAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existsByProductCode(String code, Long excludedProductId) {
        return exists("select count(*) from product_spu where product_code = ?", code, excludedProductId);
    }

    @Override
    public boolean existsByItemNo(String itemNo, Long excludedProductId) {
        return exists("select count(*) from product_spu where item_no = ?", itemNo, excludedProductId);
    }

    @Override
    public boolean existsBySkuCode(String code, Long excludedProductId) {
        return exists("select count(*) from product_sku where sku_code = ?", code, excludedProductId);
    }

    @Override
    public boolean existsByBarcode(String barcode, Long excludedProductId) {
        return exists("select count(*) from product_sku where barcode = ?", barcode, excludedProductId);
    }

    @Override
    public Product save(Product product) {
        var productId = product.id() == null ? insertProduct(product) : updateProduct(product);
        var existingSkuIds = existingSkuIds(productId);
        clearSpecifications(productId);
        var savedSkus = product.skus().stream().map(sku -> saveSku(productId, sku)).toList();
        deleteRemovedSkus(productId, existingSkuIds, savedSkus);
        var specificationIds = insertSpecifications(productId, product.specifications());
        var skuSpecifications = product.specifications().stream()
                .filter(specification -> !PRODUCT_DIMENSION_NAMES.contains(specification.name()))
                .toList();
        insertSkuSpecificationValues(productId, savedSkus, skuSpecifications, specificationIds);
        return findById(productId).orElseThrow(this::notFound);
    }

    @Override
    public String nextProductCode() {
        var nextValue = jdbc.queryForObject(
                "select next_value from business_code_sequence where sequence_name = 'product' for update",
                Long.class
        );
        if (nextValue == null) {
            throw new IllegalStateException("产品编码序列不存在");
        }
        var maxExistingNextValue = jdbc.queryForObject(
                "select coalesce(max(cast(substring(product_code, 5) as unsigned)), 0) + 1 "
                        + "from product_spu where product_code regexp '^PRD-[0-9]{6}$'",
                Long.class
        );
        nextValue = Math.max(nextValue, maxExistingNextValue == null ? 1 : maxExistingNextValue);
        jdbc.update(
                "update business_code_sequence set next_value = ? where sequence_name = 'product'",
                nextValue + 1
        );
        return "PRD-%06d".formatted(nextValue);
    }

    @Override
    public Optional<Product> findById(long id) {
        var products = jdbc.query(
                "select " + PRODUCT_COLUMNS + " from product_spu where id = ?",
                (resultSet, rowNumber) -> productRow(resultSet), id
        );
        if (products.isEmpty()) {
            return Optional.empty();
        }
        var row = products.getFirst();
        var specifications = findSpecifications(id);
        var skus = findSkus(id);
        return Optional.of(new Product(
                row.id(), row.code(), row.itemNo(), row.name(), row.categoryId(), row.brand(),
                row.type(), row.mainImageFileId(), row.status(), row.remark(), specifications, skus,
                row.createdAt(), row.updatedAt()
        ));
    }

    @Override
    public Optional<Sku> findSku(long id) {
        var productIds = jdbc.queryForList(
                "select product_id from product_sku where id = ?", Long.class, id
        );
        if (productIds.isEmpty()) {
            return Optional.empty();
        }
        return findById(productIds.getFirst()).flatMap(product -> product.skus().stream()
                .filter(sku -> sku.id().equals(id))
                .findFirst());
    }

    @Override
    public Optional<Product> findProductBySkuId(long skuId) {
        var productIds = jdbc.queryForList(
                "select product_id from product_sku where id = ?", Long.class, skuId
        );
        return productIds.isEmpty() ? Optional.empty() : findById(productIds.getFirst());
    }

    @Override
    public void updateSkuStandardCost(long id, java.math.BigDecimal standardCost) {
        var updated = jdbc.update(
                "update product_sku set standard_cost = ?, updated_at = now(3) where id = ?",
                standardCost, id
        );
        if (updated != 1) {
            throw new BusinessException("SKU_NOT_FOUND", HttpStatus.NOT_FOUND, "SKU 不存在");
        }
    }

    @Override
    public Page<Product> findAll(
            String keyword,
            Long categoryId,
            Long supplierId,
            String status,
            Pageable pageable
    ) {
        var where = new ArrayList<String>();
        var parameters = new ArrayList<Object>();
        var categoryCte = "";
        if (categoryId != null) {
            categoryCte = "with recursive selected_categories as ("
                    + "select id from product_category where id = ? "
                    + "union all "
                    + "select child.id from product_category child "
                    + "join selected_categories parent on child.parent_id = parent.id"
                    + ") ";
            parameters.add(categoryId);
        }
        if (keyword != null) {
            where.add("(product.product_code like ? or product.item_no like ? or product.product_name like ? "
                    + "or exists (select 1 from product_sku sku where sku.product_id = product.id "
                    + "and (sku.sku_code like ? or sku.barcode like ?)))");
            var pattern = "%" + keyword + "%";
            parameters.add(pattern);
            parameters.add(pattern);
            parameters.add(pattern);
            parameters.add(pattern);
            parameters.add(pattern);
        }
        if (categoryId != null) {
            where.add("product.category_id in (select id from selected_categories)");
        }
        if (supplierId != null) {
            where.add("exists (select 1 from sku_supplier_quote quote "
                    + "join product_sku sku on sku.id = quote.sku_id "
                    + "where sku.product_id = product.id and quote.supplier_id = ? and quote.status = 'enabled')");
            parameters.add(supplierId);
        }
        if (status != null) {
            where.add("product.status = ?");
            parameters.add(status);
        }
        var whereSql = where.isEmpty() ? "" : " where " + String.join(" and ", where);
        var count = jdbc.queryForObject(
                categoryCte + "select count(*) from product_spu product" + whereSql,
                Long.class,
                parameters.toArray()
        );
        var pageParameters = new ArrayList<>(parameters);
        pageParameters.add(pageable.getPageSize());
        pageParameters.add(pageable.getOffset());
        var ids = jdbc.queryForList(
                categoryCte + "select product.id from product_spu product" + whereSql
                        + " order by product.product_name asc, product.id asc limit ? offset ?",
                Long.class,
                pageParameters.toArray()
        );
        var products = ids.stream().map(this::findById).flatMap(Optional::stream).toList();
        return new PageImpl<>(products, pageable, count == null ? 0 : count);
    }

    @Override
    public Optional<String> findMainImageUrl(long productId) {
        return jdbc.query(
                "select file.access_url from product_spu product "
                        + "join file_asset file on file.id = product.main_image_file_id "
                        + "where product.id = ? and file.status = 'enabled'",
                (resultSet, rowNumber) -> resultSet.getString("access_url"), productId
        ).stream().findFirst();
    }

    @Override
    public Map<Long, String> findImageUrls(long productId) {
        var imageUrls = new HashMap<Long, String>();
        jdbc.query(
                "select file.id, file.access_url from file_asset file "
                        + "where file.status = 'enabled' and ("
                        + "file.id = (select main_image_file_id from product_spu where id = ?) "
                        + "or file.id in (select sku_image_file_id from product_sku where product_id = ?) "
                        + "or file.id in (select package_image_file_id from product_sku where product_id = ?) "
                        + "or file.id in (select carton_image_file_id from product_sku where product_id = ?))",
                (RowCallbackHandler) resultSet -> imageUrls.put(resultSet.getLong("id"), resultSet.getString("access_url")),
                productId, productId, productId, productId
        );
        return imageUrls;
    }

    @Override
    public Optional<String> findDefaultSupplierName(long productId) {
        return jdbc.query(
                "select group_concat(distinct supplier.supplier_name order by supplier.supplier_name separator '、') "
                        + "as supplier_names from product_sku sku "
                        + "join sku_supplier_quote quote on quote.sku_id = sku.id "
                        + "join supplier supplier on supplier.id = quote.supplier_id "
                        + "where sku.product_id = ? and quote.is_default = true "
                        + "and quote.status = 'enabled' and supplier.status = 'enabled'",
                (resultSet, rowNumber) -> resultSet.getString("supplier_names"), productId
        ).stream().filter(value -> value != null && !value.isBlank()).findFirst();
    }

    @Override
    public ProductCatalogData loadCatalogData(Collection<Long> productIds) {
        var ids = productIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return ProductCatalogData.empty();
        }
        var placeholders = placeholders(ids.size());
        return new ProductCatalogData(
                findStockQuantities(ids, placeholders),
                findSupplierQuotes(ids, placeholders),
                findDefaultSalePrices(ids, placeholders),
                findDefaultSupplierNames(ids, placeholders),
                findImageUrls(ids, placeholders)
        );
    }

    @Override
    public Map<Long, Long> categoryCounts() {
        var counts = new LinkedHashMap<Long, Long>();
        jdbc.query(
                "with recursive category_descendants as ("
                        + "select id as ancestor_id, id as descendant_id from product_category "
                        + "union all "
                        + "select tree.ancestor_id, child.id from product_category child "
                        + "join category_descendants tree on child.parent_id = tree.descendant_id"
                        + ") "
                        + "select category.id as category_id, count(distinct product.id) as product_count "
                        + "from product_category category "
                        + "left join category_descendants tree on tree.ancestor_id = category.id "
                        + "left join product_spu product on product.category_id = tree.descendant_id "
                        + "group by category.id order by category.id",
                (RowCallbackHandler) resultSet -> counts.put(
                        resultSet.getLong("category_id"), resultSet.getLong("product_count")
                )
        );
        return counts;
    }

    private Map<Long, BigDecimal> findStockQuantities(List<Long> productIds, String placeholders) {
        var quantities = new HashMap<Long, BigDecimal>();
        jdbc.query(
                "select sku.id as sku_id, coalesce(sum(balance.quantity), 0) as stock_quantity "
                        + "from product_sku sku "
                        + "left join inventory_balance balance on balance.sku_id = sku.id "
                        + "where sku.product_id in (" + placeholders + ") "
                        + "group by sku.id",
                (RowCallbackHandler) resultSet -> quantities.put(
                        resultSet.getLong("sku_id"), resultSet.getBigDecimal("stock_quantity")
                ),
                productIds.toArray()
        );
        return quantities;
    }

    private Map<Long, List<SupplierQuote>> findSupplierQuotes(List<Long> productIds, String placeholders) {
        var quotesBySkuId = new LinkedHashMap<Long, List<SupplierQuote>>();
        jdbc.query(
                "select quote.id, quote.sku_id, quote.supplier_id, quote.supplier_item_no, "
                        + "quote.purchase_price, quote.min_purchase_quantity, quote.is_default, quote.status "
                        + "from sku_supplier_quote quote "
                        + "join product_sku sku on sku.id = quote.sku_id "
                        + "where sku.product_id in (" + placeholders + ") "
                        + "order by quote.sku_id, quote.is_default desc, quote.id asc",
                (RowCallbackHandler) resultSet -> quotesBySkuId.computeIfAbsent(
                        resultSet.getLong("sku_id"), ignored -> new ArrayList<>()
                ).add(new SupplierQuote(
                        resultSet.getLong("id"),
                        resultSet.getLong("sku_id"),
                        resultSet.getLong("supplier_id"),
                        resultSet.getString("supplier_item_no"),
                        resultSet.getBigDecimal("purchase_price"),
                        resultSet.getBigDecimal("min_purchase_quantity"),
                        resultSet.getBoolean("is_default"),
                        resultSet.getString("status")
                )),
                productIds.toArray()
        );
        return quotesBySkuId;
    }

    private Map<Long, BigDecimal> findDefaultSalePrices(List<Long> productIds, String placeholders) {
        var prices = new HashMap<Long, BigDecimal>();
        jdbc.query(
                "select product_id, default_sale_price from product_sku "
                        + "where product_id in (" + placeholders + ") and is_default = true "
                        + "order by id",
                (RowCallbackHandler) resultSet -> prices.putIfAbsent(
                        resultSet.getLong("product_id"), resultSet.getBigDecimal("default_sale_price")
                ),
                productIds.toArray()
        );
        return prices;
    }

    private Map<Long, String> findDefaultSupplierNames(List<Long> productIds, String placeholders) {
        var names = new HashMap<Long, String>();
        jdbc.query(
                "select sku.product_id, "
                        + "group_concat(distinct supplier.supplier_name order by supplier.supplier_name separator '、') "
                        + "as supplier_names from product_sku sku "
                        + "join sku_supplier_quote quote on quote.sku_id = sku.id "
                        + "join supplier supplier on supplier.id = quote.supplier_id "
                        + "where sku.product_id in (" + placeholders + ") "
                        + "and quote.is_default = true and quote.status = 'enabled' and supplier.status = 'enabled' "
                        + "group by sku.product_id",
                (RowCallbackHandler) resultSet -> names.put(
                        resultSet.getLong("product_id"), resultSet.getString("supplier_names")
                ),
                productIds.toArray()
        );
        return names;
    }

    private Map<Long, String> findImageUrls(List<Long> productIds, String placeholders) {
        var urls = new HashMap<Long, String>();
        var parameters = new ArrayList<Object>();
        for (var ignored = 0; ignored < 4; ignored++) {
            parameters.addAll(productIds);
        }
        jdbc.query(
                "select file.id, file.access_url from file_asset file "
                        + "join ("
                        + "select main_image_file_id as file_id from product_spu where id in (" + placeholders + ") "
                        + "union select sku_image_file_id from product_sku where product_id in (" + placeholders + ") "
                        + "union select package_image_file_id from product_sku where product_id in (" + placeholders + ") "
                        + "union select carton_image_file_id from product_sku where product_id in (" + placeholders + ")"
                        + ") image_ref on image_ref.file_id = file.id "
                        + "where file.status = 'enabled'",
                (RowCallbackHandler) resultSet -> urls.put(
                        resultSet.getLong("id"), resultSet.getString("access_url")
                ),
                parameters.toArray()
        );
        return urls;
    }

    private boolean exists(String sql, String value, Long excludedProductId) {
        var effectiveSql = excludedProductId == null
                ? sql
                : sql + (sql.contains("product_sku") ? " and product_id <> ?" : " and id <> ?");
        var count = excludedProductId == null
                ? jdbc.queryForObject(effectiveSql, Long.class, value)
                : jdbc.queryForObject(effectiveSql, Long.class, value, excludedProductId);
        return count != null && count > 0;
    }

    private long insertProduct(Product product) {
        return insertAndReturnId(
                "insert into product_spu (product_code, item_no, product_name, category_id, brand, product_type, "
                        + "main_image_file_id, status, remark, created_at, updated_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, now(3), now(3))",
                product.code(), product.itemNo(), product.name(), product.categoryId(), product.brand(),
                typeValue(product.type()), product.mainImageFileId(), product.status(), product.remark()
        );
    }

    private long updateProduct(Product product) {
        var updated = jdbc.update(
                "update product_spu set product_code=?, item_no=?, product_name=?, category_id=?, brand=?, "
                        + "product_type=?, main_image_file_id=?, status=?, remark=?, updated_at=now(3) where id=?",
                product.code(), product.itemNo(), product.name(), product.categoryId(), product.brand(),
                typeValue(product.type()), product.mainImageFileId(), product.status(), product.remark(), product.id()
        );
        if (updated != 1) {
            throw notFound();
        }
        return product.id();
    }

    private List<Long> existingSkuIds(long productId) {
        return jdbc.queryForList(
                "select id from product_sku where product_id = ?", Long.class, productId
        );
    }

    private void clearSpecifications(long productId) {
        jdbc.update("delete from product_sku_spec_value where product_id = ?", productId);
        jdbc.update("delete value from product_spec_value value join product_spec spec on spec.id = value.spec_id "
                + "where spec.product_id = ?", productId);
        jdbc.update("delete from product_spec where product_id = ?", productId);
    }

    private Sku saveSku(long productId, Sku sku) {
        var packaging = sku.packaging();
        if (sku.id() == null) {
            var id = insertAndReturnId(
                    "insert into product_sku (product_id, sku_code, barcode, sku_name, spec_text, sales_unit, "
                            + "default_sale_price, standard_cost, safety_stock_quantity, package_length_cm, package_width_cm, "
                            + "package_height_cm, package_volume_cm3, inner_package_length_cm, inner_package_width_cm, "
                            + "inner_package_height_cm, net_weight_kg, gross_weight_kg, gram_weight_g, inner_package_weight_kg, "
                            + "packaging_method, carton_quantity, sku_image_file_id, package_image_file_id, carton_image_file_id, "
                            + "is_default, status, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                            + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(3), now(3))",
                    skuParameters(productId, sku, packaging)
            );
            return sku.withId(id);
        }
        var parameters = new ArrayList<Object>();
        for (var parameter : skuParameters(productId, sku, packaging)) {
            parameters.add(parameter);
        }
        parameters.add(sku.id());
        parameters.add(productId);
        var updated = jdbc.update(
                "update product_sku set product_id=?, sku_code=?, barcode=?, sku_name=?, spec_text=?, sales_unit=?, "
                        + "default_sale_price=?, standard_cost=?, safety_stock_quantity=?, package_length_cm=?, package_width_cm=?, "
                        + "package_height_cm=?, package_volume_cm3=?, inner_package_length_cm=?, inner_package_width_cm=?, "
                        + "inner_package_height_cm=?, net_weight_kg=?, gross_weight_kg=?, gram_weight_g=?, inner_package_weight_kg=?, "
                        + "packaging_method=?, carton_quantity=?, sku_image_file_id=?, package_image_file_id=?, carton_image_file_id=?, "
                        + "is_default=?, status=?, updated_at=now(3) where id=? and product_id=?",
                parameters.toArray()
        );
        if (updated != 1) {
            throw new BusinessException("SKU_NOT_FOUND", HttpStatus.NOT_FOUND, "SKU 不存在");
        }
        return sku;
    }

    private Object[] skuParameters(long productId, Sku sku, Packaging packaging) {
        return new Object[]{
                productId, sku.code(), sku.barcode(), sku.name(), sku.specText(), sku.salesUnit(),
                sku.defaultSalePrice(), sku.standardCost(), sku.safetyStockQuantity(),
                packaging == null ? null : packaging.lengthCm(),
                packaging == null ? null : packaging.widthCm(),
                packaging == null ? null : packaging.heightCm(),
                packaging == null ? null : packaging.volumeCm3(),
                packaging == null ? null : packaging.innerLengthCm(),
                packaging == null ? null : packaging.innerWidthCm(),
                packaging == null ? null : packaging.innerHeightCm(),
                packaging == null ? null : packaging.netWeightKg(),
                packaging == null ? null : packaging.grossWeightKg(),
                packaging == null ? null : packaging.gramWeightG(),
                packaging == null ? null : packaging.innerWeightKg(),
                packaging == null ? null : packaging.method(),
                packaging == null ? null : packaging.cartonQuantity(),
                sku.skuImageFileId(),
                packaging == null ? null : packaging.packageImageFileId(),
                packaging == null ? null : packaging.cartonImageFileId(),
                sku.isDefault(), sku.status()
        };
    }

    private void deleteRemovedSkus(long productId, List<Long> existingSkuIds, List<Sku> savedSkus) {
        var retainedIds = savedSkus.stream().map(Sku::id).toList();
        for (var skuId : existingSkuIds) {
            if (!retainedIds.contains(skuId)) {
                jdbc.update("delete from product_sku where id = ? and product_id = ?", skuId, productId);
            }
        }
    }

    private Map<String, Map<String, Long>> insertSpecifications(
            long productId,
            List<Specification> specifications
    ) {
        var result = new LinkedHashMap<String, Map<String, Long>>();
        for (var specificationIndex = 0; specificationIndex < specifications.size(); specificationIndex++) {
            var specification = specifications.get(specificationIndex);
            var specificationId = insertAndReturnId(
                    "insert into product_spec (product_id, spec_name, sort_order, status, created_at, updated_at) "
                            + "values (?, ?, ?, 'enabled', now(3), now(3))",
                    productId, specification.name(), specificationIndex
            );
            var valueIds = new LinkedHashMap<String, Long>();
            for (var valueIndex = 0; valueIndex < specification.values().size(); valueIndex++) {
                var value = specification.values().get(valueIndex);
                var valueId = insertAndReturnId(
                        "insert into product_spec_value (spec_id, value_name, sort_order, status, created_at, updated_at) "
                                + "values (?, ?, ?, 'enabled', now(3), now(3))",
                        specificationId, value, valueIndex
                );
                valueIds.put(value, valueId);
            }
            result.put(specification.name(), valueIds);
        }
        return result;
    }

    private void insertSkuSpecificationValues(
            long productId,
            List<Sku> skus,
            List<Specification> specifications,
            Map<String, Map<String, Long>> specificationIds
    ) {
        for (var sku : skus) {
            if (sku.specificationValues().isEmpty()) {
                continue;
            }
            for (var index = 0; index < specifications.size(); index++) {
                var specification = specifications.get(index);
                var value = sku.specificationValues().get(index);
                var valueId = specificationIds.get(specification.name()).get(value);
                var specId = jdbc.queryForObject(
                        "select id from product_spec where product_id = ? and spec_name = ?",
                        Long.class, productId, specification.name()
                );
                jdbc.update(
                        "insert into product_sku_spec_value (product_id, sku_id, spec_id, spec_value_id, created_at, updated_at) "
                                + "values (?, ?, ?, ?, now(3), now(3))",
                        productId, sku.id(), specId, valueId
                );
            }
        }
    }

    private List<Specification> findSpecifications(long productId) {
        var rows = jdbc.query(
                "select spec.spec_name, value.value_name from product_spec spec "
                        + "join product_spec_value value on value.spec_id = spec.id "
                        + "where spec.product_id = ? order by spec.sort_order asc, spec.id asc, value.sort_order asc, value.id asc",
                (resultSet, rowNumber) -> Map.entry(
                        resultSet.getString("spec_name"), resultSet.getString("value_name")
                ), productId
        );
        var values = new LinkedHashMap<String, List<String>>();
        for (var row : rows) {
            values.computeIfAbsent(row.getKey(), ignored -> new ArrayList<>()).add(row.getValue());
        }
        return values.entrySet().stream()
                .map(entry -> new Specification(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<Sku> findSkus(long productId) {
        var specValuesBySku = new HashMap<Long, List<String>>();
        jdbc.query(
                "select link.sku_id, value.value_name from product_sku_spec_value link "
                        + "join product_spec spec on spec.id = link.spec_id "
                        + "join product_spec_value value on value.id = link.spec_value_id "
                        + "where link.product_id = ? order by link.sku_id, spec.sort_order asc, spec.id asc",
                (RowCallbackHandler) resultSet -> specValuesBySku.computeIfAbsent(
                        resultSet.getLong("sku_id"), ignored -> new ArrayList<>()
                ).add(resultSet.getString("value_name")),
                productId
        );
        return jdbc.query(
                "select id, sku_code, barcode, sku_name, spec_text, sales_unit, default_sale_price, standard_cost, "
                        + "safety_stock_quantity, package_length_cm, package_width_cm, package_height_cm, package_volume_cm3, "
                        + "inner_package_length_cm, inner_package_width_cm, inner_package_height_cm, net_weight_kg, "
                        + "gross_weight_kg, gram_weight_g, inner_package_weight_kg, packaging_method, carton_quantity, sku_image_file_id, "
                        + "package_image_file_id, carton_image_file_id, is_default, status from product_sku "
                        + "where product_id = ? order by is_default desc, id asc",
                (resultSet, rowNumber) -> {
                    var id = resultSet.getLong("id");
                    return new Sku(
                            id,
                            resultSet.getString("sku_code"),
                            resultSet.getString("barcode"),
                            resultSet.getString("sku_name"),
                            resultSet.getString("spec_text"),
                            specValuesBySku.getOrDefault(id, List.of()),
                            resultSet.getString("sales_unit"),
                            resultSet.getBigDecimal("default_sale_price"),
                            resultSet.getBigDecimal("standard_cost"),
                            resultSet.getBigDecimal("safety_stock_quantity"),
                            new Packaging(
                                    resultSet.getBigDecimal("package_length_cm"),
                                    resultSet.getBigDecimal("package_width_cm"),
                                    resultSet.getBigDecimal("package_height_cm"),
                                    resultSet.getBigDecimal("package_volume_cm3"),
                                    resultSet.getBigDecimal("inner_package_length_cm"),
                                    resultSet.getBigDecimal("inner_package_width_cm"),
                                    resultSet.getBigDecimal("inner_package_height_cm"),
                                    resultSet.getBigDecimal("net_weight_kg"),
                                    resultSet.getBigDecimal("gross_weight_kg"),
                                    resultSet.getBigDecimal("gram_weight_g"),
                                    resultSet.getBigDecimal("inner_package_weight_kg"),
                                    resultSet.getString("packaging_method"),
                                    getNullableInteger(resultSet, "carton_quantity"),
                                    getNullableLong(resultSet, "package_image_file_id"),
                                    getNullableLong(resultSet, "carton_image_file_id")
                            ),
                            getNullableLong(resultSet, "sku_image_file_id"),
                            resultSet.getBoolean("is_default"),
                            resultSet.getString("status")
                    );
                }, productId
        );
    }

    private ProductRow productRow(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new ProductRow(
                resultSet.getLong("id"),
                resultSet.getString("product_code"),
                resultSet.getString("item_no"),
                resultSet.getString("product_name"),
                resultSet.getLong("category_id"),
                resultSet.getString("brand"),
                ProductType.valueOf(resultSet.getString("product_type").toUpperCase()),
                getNullableLong(resultSet, "main_image_file_id"),
                resultSet.getString("status"),
                resultSet.getString("remark"),
                resultSet.getTimestamp("created_at").toLocalDateTime(),
                resultSet.getTimestamp("updated_at").toLocalDateTime()
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

    private Long getNullableLong(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        var value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private Integer getNullableInteger(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        var value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private String placeholders(int count) {
        return String.join(", ", Collections.nCopies(count, "?"));
    }

    private String typeValue(ProductType type) {
        return type.name().toLowerCase();
    }

    private BusinessException notFound() {
        return new BusinessException("PRODUCT_NOT_FOUND", HttpStatus.NOT_FOUND, "产品不存在");
    }

    private record ProductRow(
            Long id,
            String code,
            String itemNo,
            String name,
            Long categoryId,
            String brand,
            ProductType type,
            Long mainImageFileId,
            String status,
            String remark,
            java.time.LocalDateTime createdAt,
            java.time.LocalDateTime updatedAt
    ) {
    }
}
