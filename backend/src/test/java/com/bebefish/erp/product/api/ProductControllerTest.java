package com.bebefish.erp.product.api;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bebefish.erp.auth.domain.TokenIssuer;
import com.bebefish.erp.auth.domain.UserAccount;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductControllerTest {
    @Autowired MockMvc mvc;
    @Autowired TokenIssuer tokenIssuer;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    private String editToken;
    private String viewToken;
    private long categoryId;
    private long imageId;
    private long quoteSupplierId;
    private long secondQuoteSupplierId;

    @BeforeEach
    void setUp() {
        jdbc.update("delete balance from inventory_balance balance "
                + "join product_sku sku on sku.id = balance.sku_id "
                + "join product_spu product on product.id = sku.product_id "
                + "where product.product_code like 'T7-%' or product.item_no like 'EW432%'");
        jdbc.update("delete quote from sku_supplier_quote quote "
                + "join product_sku sku on sku.id = quote.sku_id "
                + "join product_spu product on product.id = sku.product_id "
                + "where product.product_code like 'T7-%' or product.item_no like 'EW432%'");
        jdbc.update("delete link from product_sku_spec_value link "
                + "join product_spu product on product.id = link.product_id "
                + "where product.product_code like 'T7-%' or product.item_no like 'EW432%'");
        jdbc.update("delete sku from product_sku sku join product_spu product on product.id = sku.product_id "
                + "where product.product_code like 'T7-%' or product.item_no like 'EW432%'");
        jdbc.update("delete value from product_spec_value value "
                + "join product_spec spec on spec.id = value.spec_id "
                + "join product_spu product on product.id = spec.product_id "
                + "where product.product_code like 'T7-%' or product.item_no like 'EW432%'");
        jdbc.update("delete spec from product_spec spec join product_spu product on product.id = spec.product_id "
                + "where product.product_code like 'T7-%' or product.item_no like 'EW432%'");
        jdbc.update("delete from product_spu where product_code like 'T7-%' or item_no like 'EW432%'");
        jdbc.update("update business_code_sequence set next_value = 1 where sequence_name = 'product'");
        jdbc.update("delete from file_asset where storage_name like 'T7-%'");
        jdbc.update("delete from warehouse where warehouse_no like 'T7-WH-%'");
        jdbc.update("delete from supplier where supplier_no in ('T7-SUP-1', 'T7-SUP-QUOTE', 'T7-SUP-QUOTE-2')");
        jdbc.update("delete from product_category where category_code = 'T7-CAT-GRANDCHILD'");
        jdbc.update("delete from product_category where category_code in ('T7-CAT-CHILD', 'T7-CAT-EMPTY')");
        jdbc.update("delete from product_category where category_code = 'T7-CAT'");
        jdbc.update("insert into product_category (category_code, category_name, level_no, sort_order, status, created_at, updated_at) "
                + "values ('T7-CAT', '测试分类', 1, 0, 'enabled', now(3), now(3))");
        categoryId = jdbc.queryForObject(
                "select id from product_category where category_code = 'T7-CAT'", Long.class
        );
        jdbc.update("insert into file_asset (original_name, storage_name, storage_path, access_url, content_type, "
                + "size_bytes, status, created_at, updated_at) values ('T7.png', 'T7-image.png', 'uploads/T7-image.png', "
                + "'/uploads/T7-image.png', 'image/png', 1, 'enabled', now(3), now(3))");
        imageId = jdbc.queryForObject("select id from file_asset where storage_name = 'T7-image.png'", Long.class);
        jdbc.update("insert into supplier (supplier_no, supplier_name, status, created_at, updated_at) "
                + "values ('T7-SUP-QUOTE', '商品报价供应商', 'enabled', now(3), now(3))");
        quoteSupplierId = jdbc.queryForObject(
                "select id from supplier where supplier_no = 'T7-SUP-QUOTE'", Long.class
        );
        jdbc.update("insert into supplier (supplier_no, supplier_name, status, created_at, updated_at) "
                + "values ('T7-SUP-QUOTE-2', '商品报价供应商二', 'enabled', now(3), now(3))");
        secondQuoteSupplierId = jdbc.queryForObject(
                "select id from supplier where supplier_no = 'T7-SUP-QUOTE-2'", Long.class
        );
        editToken = token("product:view", "product:edit");
        viewToken = token("product:view");
    }

    @Test
    void createsListsReadsUpdatesAndChangesProductStatus() throws Exception {
        JsonNode created = createProduct();
        var productId = created.path("id").asLong();
        var skuId = created.path("skus").get(0).path("id").asLong();
        var createdAt = created.path("createdAt").asText();

        mvc.perform(get("/api/products")
                        .header("Authorization", bearer(editToken))
                        .param("keyword", "EW43245")
                        .param("categoryId", Long.toString(categoryId))
                        .param("status", "enabled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].itemNo").value("EW43245"));

        mvc.perform(get("/api/products/{id}", productId)
                        .header("Authorization", bearer(editToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.skus[0].safetyStockQuantity").value(12.5))
                .andExpect(jsonPath("$.data.skus[0].packageVolumeCm3").value(36456))
                .andExpect(jsonPath("$.data.skus[0].innerPackageLengthCm").value(36))
                .andExpect(jsonPath("$.data.skus[0].innerPackageWidthCm").value(25))
                .andExpect(jsonPath("$.data.skus[0].innerPackageHeightCm").value(22))
                .andExpect(jsonPath("$.data.skus[0].innerPackageWeightKg").value(1.1))
                .andExpect(jsonPath("$.data.skus[0].cartonQuantity").value(12));

        var update = requestBody("T7-P100", "EW43245", "更新后的红酒杯", skuId);
        mvc.perform(put("/api/products/{id}", productId)
                        .header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productName").value("更新后的红酒杯"))
                .andExpect(jsonPath("$.data.createdAt").value(createdAt))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.skus[0].id").value(skuId));

        mvc.perform(post("/api/products/{id}/status", productId)
                        .header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON)
                        .content("{\"status\":\"disabled\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("disabled"));
    }

    @Test
    void rejectsCreateForViewOnlyUser() throws Exception {
        mvc.perform(post("/api/products")
                        .header("Authorization", bearer(viewToken))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody("T7-P200", "EW43246", "红酒杯", null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void generatesProductCodeWhenCreateRequestOmitsIt() throws Exception {
        mvc.perform(post("/api/products")
                        .header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody(null, "EW43249", "自动编码产品", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productCode").value(org.hamcrest.Matchers.matchesPattern("PRD-\\d{6}")));
    }

    @Test
    void persistsProductDimensionsWhenCreatingAndUpdatingSimpleProduct() throws Exception {
        var request = requestBody(null, "EW43250", "尺寸参数产品", null);
        request.put("specifications", List.of(
                Map.of("name", "口径", "values", List.of("70±1mm")),
                Map.of("name", "容量", "values", List.of("210ml"))
        ));

        var created = mvc.perform(post("/api/products")
                        .header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.specifications[0].name").value("口径"))
                .andExpect(jsonPath("$.data.specifications[0].values[0]").value("70±1mm"))
                .andReturn().getResponse().getContentAsString();
        var product = objectMapper.readTree(created).path("data");
        var productId = product.path("id").asLong();
        var skuId = product.path("skus").get(0).path("id").asLong();

        var update = requestBody("T7-P-DIM", "EW43250", "更新后的尺寸参数产品", skuId);
        update.put("specifications", List.of(Map.of("name", "重量", "values", List.of("200±12g"))));

        mvc.perform(put("/api/products/{id}", productId)
                        .header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.specifications[0].name").value("重量"))
                .andExpect(jsonPath("$.data.specifications[0].values[0]").value("200±12g"));
    }

    @Test
    void createsProductWhenProductCodeSequenceFallsBehindExistingProductCodes() throws Exception {
        createProduct();
        jdbc.update("update business_code_sequence set next_value = 1 where sequence_name = 'product'");

        mvc.perform(post("/api/products")
                        .header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody(null, "EW43251", "序列恢复产品", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productCode").value("PRD-000002"));
    }

    @Test
    void savesSupplierQuotesWithProduct() throws Exception {
        var request = requestBody(null, "EW43252", "带报价商品", null);
        skuInput(request).put("supplierQuotes", List.of(Map.of(
                "supplierId", quoteSupplierId,
                "supplierItemNo", "SUP-PUMP-01",
                "purchasePrice", "61.20",
                "minPurchaseQuantity", "12",
                "defaultQuote", true,
                "status", "enabled"
        )));

        var response = mvc.perform(post("/api/products")
                        .header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var skuId = objectMapper.readTree(response).path("data").path("skus").get(0).path("id").asLong();
        var quote = jdbc.queryForMap(
                "select supplier_item_no, purchase_price, is_default from sku_supplier_quote where sku_id = ?",
                skuId
        );

        assertThat(quote.get("supplier_item_no")).isEqualTo("SUP-PUMP-01");
        assertThat(quote.get("purchase_price").toString()).startsWith("61.20");
        assertThat(quote.get("is_default")).isEqualTo(true);
    }

    @Test
    void rollsBackProductWhenSupplierQuoteReferencesMissingSupplier() throws Exception {
        var request = requestBody(null, "EW43253", "无效报价商品", null);
        skuInput(request).put("supplierQuotes", List.of(
                quoteInput(quoteSupplierId, "VALID-BEFORE-FAILURE", "60.20", true),
                quoteInput(Long.MAX_VALUE, "INVALID-SUPPLIER", "61.20", false)
        ));

        mvc.perform(post("/api/products")
                        .header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isBetween(400, 599));

        var productCount = jdbc.queryForObject(
                "select count(*) from product_spu where item_no = 'EW43253'", Long.class
        );
        assertThat(productCount).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from sku_supplier_quote where supplier_item_no = 'VALID-BEFORE-FAILURE'",
                Long.class
        )).isZero();
    }

    @Test
    void rejectsNullSupplierQuoteElementWithBadRequest() throws Exception {
        var request = requestBody(null, "EW43258", "空报价元素商品", null);
        skuInput(request).put("supplierQuotes", Collections.singletonList(null));

        mvc.perform(post("/api/products")
                        .header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(jdbc.queryForObject(
                "select count(*) from product_spu where item_no = 'EW43258'", Long.class
        )).isZero();
    }

    @Test
    void distinguishesOmittedAndEmptySupplierQuoteListsOnUpdate() throws Exception {
        var create = requestBody(null, "EW43254", "报价更新商品", null);
        skuInput(create).put("supplierQuotes", List.of(quoteInput("SUP-KEEP", "10.20")));
        var response = mvc.perform(post("/api/products")
                        .header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var product = objectMapper.readTree(response).path("data");
        var productId = product.path("id").asLong();
        var skuId = product.path("skus").get(0).path("id").asLong();

        var omitted = requestBody(null, "EW43254", "未提交报价字段", skuId);
        mvc.perform(put("/api/products/{id}", productId)
                        .header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(omitted)))
                .andExpect(status().isOk());
        assertThat(quoteCount(skuId)).isEqualTo(1);

        var empty = requestBody(null, "EW43254", "清空报价", skuId);
        skuInput(empty).put("supplierQuotes", List.of());
        mvc.perform(put("/api/products/{id}", productId)
                        .header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(empty)))
                .andExpect(status().isOk());
        assertThat(quoteCount(skuId)).isZero();
    }

    @Test
    void deletesQuotesBeforeRemovingSkuFromProduct() throws Exception {
        var create = requestBody(null, "EW43255", "多规格报价商品", null);
        create.put("productType", "variant");
        var firstSku = new LinkedHashMap<>(skuInput(create));
        firstSku.put("skuCode", "T7-QUOTE-SKU-1");
        firstSku.put("skuName", "保留款");
        firstSku.put("supplierQuotes", List.of(quoteInput("SUP-KEEP", "10.20")));
        var secondSku = new LinkedHashMap<>(skuInput(create));
        secondSku.put("skuCode", "T7-QUOTE-SKU-2");
        secondSku.put("skuName", "删除款");
        secondSku.put("supplierQuotes", List.of(quoteInput("SUP-REMOVE", "11.20")));
        create.put("skus", List.of(firstSku, secondSku));

        var response = mvc.perform(post("/api/products")
                        .header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var product = objectMapper.readTree(response).path("data");
        var productId = product.path("id").asLong();
        var retainedSkuId = product.path("skus").get(0).path("id").asLong();
        var removedSkuId = product.path("skus").get(1).path("id").asLong();

        var update = requestBody(null, "EW43255", "删除一个规格", retainedSkuId);
        update.put("productType", "variant");
        skuInput(update).put("skuCode", "T7-QUOTE-SKU-1");
        skuInput(update).put("skuName", "保留款");
        mvc.perform(put("/api/products/{id}", productId)
                        .header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());

        assertThat(quoteCount(retainedSkuId)).isEqualTo(1);
        assertThat(quoteCount(removedSkuId)).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from product_sku where id = ?", Long.class, removedSkuId
        )).isZero();
    }

    @Test
    void switchesDefaultQuoteRegardlessOfRequestOrder() throws Exception {
        var create = requestBody(null, "EW43256", "默认报价切换商品", null);
        skuInput(create).put("supplierQuotes", List.of(
                quoteInput(quoteSupplierId, "SUP-DEFAULT-1", "10.20", true),
                quoteInput(secondQuoteSupplierId, "SUP-DEFAULT-2", "9.80", false)
        ));
        var response = mvc.perform(post("/api/products")
                        .header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var product = objectMapper.readTree(response).path("data");
        var productId = product.path("id").asLong();
        var skuId = product.path("skus").get(0).path("id").asLong();
        var firstQuoteId = jdbc.queryForObject(
                "select id from sku_supplier_quote where sku_id = ? and supplier_id = ?",
                Long.class, skuId, quoteSupplierId
        );
        var secondQuoteId = jdbc.queryForObject(
                "select id from sku_supplier_quote where sku_id = ? and supplier_id = ?",
                Long.class, skuId, secondQuoteSupplierId
        );

        var update = requestBody(null, "EW43256", "已切换默认报价", skuId);
        var secondQuote = new LinkedHashMap<>(
                quoteInput(secondQuoteSupplierId, "SUP-DEFAULT-2", "9.80", true)
        );
        secondQuote.put("id", secondQuoteId);
        var firstQuote = new LinkedHashMap<>(
                quoteInput(quoteSupplierId, "SUP-DEFAULT-1", "10.20", false)
        );
        firstQuote.put("id", firstQuoteId);
        skuInput(update).put("supplierQuotes", List.of(secondQuote, firstQuote));

        mvc.perform(put("/api/products/{id}", productId)
                        .header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                "select is_default from sku_supplier_quote where id = ?", Boolean.class, secondQuoteId
        )).isTrue();
        assertThat(jdbc.queryForObject(
                "select count(*) from sku_supplier_quote where sku_id = ? and is_default = true",
                Long.class, skuId
        )).isEqualTo(1);
    }

    @Test
    void swapsSuppliersBetweenExistingQuotes() throws Exception {
        var create = requestBody(null, "EW43257", "交换报价供应商商品", null);
        skuInput(create).put("supplierQuotes", List.of(
                quoteInput(quoteSupplierId, "SUP-SWAP-1", "10.20", true),
                quoteInput(secondQuoteSupplierId, "SUP-SWAP-2", "9.80", false)
        ));
        var response = mvc.perform(post("/api/products")
                        .header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var product = objectMapper.readTree(response).path("data");
        var productId = product.path("id").asLong();
        var skuId = product.path("skus").get(0).path("id").asLong();
        var firstQuoteId = jdbc.queryForObject(
                "select id from sku_supplier_quote where sku_id = ? and supplier_id = ?",
                Long.class, skuId, quoteSupplierId
        );
        var secondQuoteId = jdbc.queryForObject(
                "select id from sku_supplier_quote where sku_id = ? and supplier_id = ?",
                Long.class, skuId, secondQuoteSupplierId
        );

        var movedToSecondSupplier = new LinkedHashMap<>(
                quoteInput(secondQuoteSupplierId, "SUP-SWAPPED-2", "8.80", false)
        );
        movedToSecondSupplier.put("id", firstQuoteId);
        var movedToFirstSupplier = new LinkedHashMap<>(
                quoteInput(quoteSupplierId, "SUP-SWAPPED-1", "9.10", true)
        );
        movedToFirstSupplier.put("id", secondQuoteId);
        var update = requestBody(null, "EW43257", "已交换报价供应商", skuId);
        skuInput(update).put("supplierQuotes", List.of(movedToSecondSupplier, movedToFirstSupplier));

        mvc.perform(put("/api/products/{id}", productId)
                        .header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());

        var firstSupplierQuote = jdbc.queryForMap(
                "select supplier_item_no, purchase_price, is_default from sku_supplier_quote "
                        + "where sku_id = ? and supplier_id = ?",
                skuId, quoteSupplierId
        );
        var secondSupplierQuote = jdbc.queryForMap(
                "select supplier_item_no, purchase_price, is_default from sku_supplier_quote "
                        + "where sku_id = ? and supplier_id = ?",
                skuId, secondQuoteSupplierId
        );
        assertThat(quoteCount(skuId)).isEqualTo(2);
        assertThat(firstSupplierQuote.get("supplier_item_no")).isEqualTo("SUP-SWAPPED-1");
        assertThat(firstSupplierQuote.get("purchase_price").toString()).startsWith("9.10");
        assertThat(firstSupplierQuote.get("is_default")).isEqualTo(true);
        assertThat(secondSupplierQuote.get("supplier_item_no")).isEqualTo("SUP-SWAPPED-2");
        assertThat(secondSupplierQuote.get("purchase_price").toString()).startsWith("8.80");
        assertThat(secondSupplierQuote.get("is_default")).isEqualTo(false);
    }

    @Test
    void returnsImageAndDefaultSupplierForProductListDisplay() throws Exception {
        JsonNode created = createProduct();
        var skuId = created.path("skus").get(0).path("id").asLong();
        jdbc.update("insert into supplier (supplier_no, supplier_name, status, created_at, updated_at) "
                + "values ('T7-SUP-1', '义乌玻璃厂', 'enabled', now(3), now(3))");
        var supplierId = jdbc.queryForObject("select id from supplier where supplier_no = 'T7-SUP-1'", Long.class);
        jdbc.update("insert into sku_supplier_quote (sku_id, supplier_id, purchase_price, min_purchase_quantity, "
                + "is_default, status, created_at, updated_at) values (?, ?, 8.00, 1, true, 'enabled', now(3), now(3))",
                skuId, supplierId);

        mvc.perform(get("/api/products")
                        .header("Authorization", bearer(editToken))
                        .param("keyword", "EW43245"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].mainImageUrl").value("/uploads/T7-image.png"))
                .andExpect(jsonPath("$.data.records[0].skus[0].skuImageUrl").value("/uploads/T7-image.png"))
                .andExpect(jsonPath("$.data.records[0].skus[0].packageImageUrl").value("/uploads/T7-image.png"))
                .andExpect(jsonPath("$.data.records[0].skus[0].cartonImageUrl").value("/uploads/T7-image.png"))
                .andExpect(jsonPath("$.data.records[0].defaultSupplierName").value("义乌玻璃厂"));
    }

    @Test
    void returnsBatchCatalogMetricsAndAllSkuSupplierQuotes() throws Exception {
        var request = requestBody(null, "EW43259", "聚合读模型商品", null);
        request.put("productType", "variant");
        var firstSku = new LinkedHashMap<>(skuInput(request));
        firstSku.put("skuCode", "T7-CATALOG-SKU-1");
        firstSku.put("skuName", "默认 SKU");
        firstSku.put("defaultSalePrice", "9.90");
        firstSku.put("safetyStockQuantity", "5.5");
        firstSku.put("supplierQuotes", List.of(
                quoteInput(quoteSupplierId, "T7-DEFAULT-QUOTE", "4.20", true),
                Map.of(
                        "supplierId", secondQuoteSupplierId,
                        "supplierItemNo", "T7-DISABLED-QUOTE",
                        "purchasePrice", "4.00",
                        "minPurchaseQuantity", "2",
                        "defaultQuote", false,
                        "status", "disabled"
                )
        ));
        var secondSku = new LinkedHashMap<>(skuInput(request));
        secondSku.put("skuCode", "T7-CATALOG-SKU-2");
        secondSku.put("skuName", "第二 SKU");
        secondSku.put("defaultSalePrice", "19.90");
        secondSku.put("safetyStockQuantity", "7.0");
        secondSku.put("supplierQuotes", List.of(
                quoteInput(secondQuoteSupplierId, "T7-SECOND-DEFAULT", "8.40", true)
        ));
        request.put("skus", List.of(firstSku, secondSku));

        var response = mvc.perform(post("/api/products")
                        .header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var product = objectMapper.readTree(response).path("data");
        var productId = product.path("id").asLong();
        var firstSkuId = product.path("skus").get(0).path("id").asLong();
        var secondSkuId = product.path("skus").get(1).path("id").asLong();
        var firstWarehouseId = createWarehouse("T7-WH-1", "测试仓一");
        var secondWarehouseId = createWarehouse("T7-WH-2", "测试仓二");
        insertBalance(firstWarehouseId, firstSkuId, "6.25");
        insertBalance(secondWarehouseId, firstSkuId, "3.25");
        insertBalance(firstWarehouseId, secondSkuId, "4.00");
        insertBalance(secondWarehouseId, secondSkuId, "5.00");

        mvc.perform(get("/api/products")
                        .header("Authorization", bearer(viewToken))
                        .param("keyword", "EW43259"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].totalStock").value(18.5))
                .andExpect(jsonPath("$.data.records[0].totalSafetyStock").value(12.5))
                .andExpect(jsonPath("$.data.records[0].defaultSalePrice").value(9.9))
                .andExpect(jsonPath("$.data.records[0].completenessPercent").value(100))
                .andExpect(jsonPath("$.data.records[0].completenessStatus").value("complete"))
                .andExpect(jsonPath("$.data.records[0].missingGroups").isEmpty())
                .andExpect(jsonPath("$.data.records[0].skus[0].stockQuantity").value(9.5))
                .andExpect(jsonPath("$.data.records[0].skus[1].stockQuantity").value(9.0));

        mvc.perform(get("/api/products/{id}", productId)
                        .header("Authorization", bearer(viewToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalStock").value(18.5))
                .andExpect(jsonPath("$.data.skus[0].supplierQuotes.length()").value(2))
                .andExpect(jsonPath("$.data.skus[0].supplierQuotes[0].defaultQuote").value(true))
                .andExpect(jsonPath("$.data.skus[0].supplierQuotes[1].status").value("disabled"))
                .andExpect(jsonPath("$.data.skus[1].supplierQuotes.length()").value(1))
                .andExpect(jsonPath("$.data.mainImageUrl").value("/uploads/T7-image.png"));
    }

    @Test
    void includesDeepDescendantsInCategoryFilterAndCategoryCounts() throws Exception {
        var childCategoryId = createCategory("T7-CAT-CHILD", "测试子分类", categoryId, 2);
        var grandchildCategoryId = createCategory("T7-CAT-GRANDCHILD", "测试孙分类", childCategoryId, 3);
        var emptyCategoryId = createCategory("T7-CAT-EMPTY", "测试空分类", categoryId, 2);
        var request = requestBody(null, "EW43260", "孙分类商品", null);
        request.put("categoryId", grandchildCategoryId);
        mvc.perform(post("/api/products")
                        .header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/products")
                        .header("Authorization", bearer(viewToken))
                        .param("categoryId", Long.toString(categoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].itemNo").value("EW43260"));

        var countsResponse = mvc.perform(get("/api/products/category-counts")
                        .header("Authorization", bearer(viewToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var counts = objectMapper.readTree(countsResponse).path("data");
        assertThat(counts.path(Long.toString(categoryId)).asLong()).isEqualTo(1);
        assertThat(counts.path(Long.toString(childCategoryId)).asLong()).isEqualTo(1);
        assertThat(counts.path(Long.toString(grandchildCategoryId)).asLong()).isEqualTo(1);
        assertThat(counts.path(Long.toString(emptyCategoryId)).asLong()).isZero();
    }

    private JsonNode createProduct() throws Exception {
        var response = mvc.perform(post("/api/products")
                        .header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody(null, "EW43245", "红酒杯", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productCode").value("PRD-000001"))
                .andExpect(jsonPath("$.data.skus[0].skuCode").value("PRD-000001-DEFAULT"))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private Map<String, Object> requestBody(
            String productCode,
            String itemNo,
            String productName,
            Long skuId
    ) {
        var sku = new LinkedHashMap<String, Object>();
        sku.put("id", skuId);
        sku.put("salesUnit", "只");
        sku.put("defaultSalePrice", "9.90");
        sku.put("standardCost", "2.20");
        sku.put("safetyStockQuantity", "12.5");
        sku.put("packageLengthCm", "42");
        sku.put("packageWidthCm", "31");
        sku.put("packageHeightCm", "28");
        sku.put("innerPackageLengthCm", "36");
        sku.put("innerPackageWidthCm", "25");
        sku.put("innerPackageHeightCm", "22");
        sku.put("netWeightKg", "8.5");
        sku.put("grossWeightKg", "9.2");
        sku.put("gramWeightG", "350");
        sku.put("innerPackageWeightKg", "1.1");
        sku.put("packagingMethod", "彩盒");
        sku.put("cartonQuantity", 12);
        sku.put("skuImageFileId", imageId);
        sku.put("packageImageFileId", imageId);
        sku.put("cartonImageFileId", imageId);
        var product = new LinkedHashMap<String, Object>();
        if (productCode != null) {
            product.put("productCode", productCode);
        }
        product.put("itemNo", itemNo);
        product.put("productName", productName);
        product.put("categoryId", categoryId);
        product.put("brand", "共典");
        product.put("productType", "simple");
        product.put("mainImageFileId", imageId);
        product.put("specifications", List.of());
        product.put("skus", List.of(sku));
        return product;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> skuInput(Map<String, Object> product) {
        return (Map<String, Object>) ((List<?>) product.get("skus")).getFirst();
    }

    private Map<String, Object> quoteInput(String supplierItemNo, String purchasePrice) {
        return quoteInput(quoteSupplierId, supplierItemNo, purchasePrice, true);
    }

    private Map<String, Object> quoteInput(
            long supplierId,
            String supplierItemNo,
            String purchasePrice,
            boolean defaultQuote
    ) {
        return Map.of(
                "supplierId", supplierId,
                "supplierItemNo", supplierItemNo,
                "purchasePrice", purchasePrice,
                "minPurchaseQuantity", "1",
                "defaultQuote", defaultQuote,
                "status", "enabled"
        );
    }

    private long quoteCount(long skuId) {
        return jdbc.queryForObject(
                "select count(*) from sku_supplier_quote where sku_id = ?", Long.class, skuId
        );
    }

    private long createWarehouse(String warehouseNo, String warehouseName) {
        jdbc.update("insert into warehouse (warehouse_no, warehouse_name, is_default, status, created_at, updated_at) "
                + "values (?, ?, false, 'enabled', now(3), now(3))", warehouseNo, warehouseName);
        return jdbc.queryForObject(
                "select id from warehouse where warehouse_no = ?", Long.class, warehouseNo
        );
    }

    private void insertBalance(long warehouseId, long skuId, String quantity) {
        jdbc.update("insert into inventory_balance "
                        + "(warehouse_id, sku_id, quantity, version_no, created_at, updated_at) "
                        + "values (?, ?, ?, 0, now(3), now(3))",
                warehouseId, skuId, quantity);
    }

    private long createCategory(String code, String name, long parentId, int level) {
        jdbc.update("insert into product_category "
                        + "(category_code, category_name, parent_id, level_no, sort_order, status, created_at, updated_at) "
                        + "values (?, ?, ?, ?, 0, 'enabled', now(3), now(3))",
                code, name, parentId, level);
        return jdbc.queryForObject(
                "select id from product_category where category_code = ?", Long.class, code
        );
    }

    private String token(String... permissions) {
        return tokenIssuer.issue(new UserAccount("13900000004", "unused", true, true,
                List.of("TESTER"), List.of(permissions)), "test").accessToken();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
