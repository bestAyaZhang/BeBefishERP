package com.bebefish.erp.product.api;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bebefish.erp.auth.domain.TokenIssuer;
import com.bebefish.erp.auth.domain.UserAccount;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @BeforeEach
    void setUp() {
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
        jdbc.update("delete from supplier where supplier_no = 'T7-SUP-1'");
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
        editToken = token("product:view", "product:edit");
        viewToken = token("product:view");
    }

    @Test
    void createsListsReadsUpdatesAndChangesProductStatus() throws Exception {
        JsonNode created = createProduct();
        var productId = created.path("id").asLong();
        var skuId = created.path("skus").get(0).path("id").asLong();

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
                .andExpect(jsonPath("$.data.skus[0].packageVolumeCm3").value(36456))
                .andExpect(jsonPath("$.data.skus[0].cartonQuantity").value(12));

        var update = requestBody("T7-P100", "EW43245", "更新后的红酒杯", skuId);
        mvc.perform(put("/api/products/{id}", productId)
                        .header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productName").value("更新后的红酒杯"))
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

    private JsonNode createProduct() throws Exception {
        var response = mvc.perform(post("/api/products")
                        .header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody(null, "EW43245", "红酒杯", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productCode").value("PRD-000001"))
                .andExpect(jsonPath("$.data.skus[0].skuCode").value("PRD-000001-DEFAULT"))
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
        sku.put("packageLengthCm", "42");
        sku.put("packageWidthCm", "31");
        sku.put("packageHeightCm", "28");
        sku.put("netWeightKg", "8.5");
        sku.put("grossWeightKg", "9.2");
        sku.put("gramWeightG", "350");
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

    private String token(String... permissions) {
        return tokenIssuer.issue(new UserAccount("13900000004", "unused", true, true,
                List.of("TESTER"), List.of(permissions)), "test").accessToken();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
