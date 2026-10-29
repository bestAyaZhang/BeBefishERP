package com.bebefish.erp.product.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.bebefish.erp.auth.domain.TokenIssuer;
import com.bebefish.erp.support.TestAuthTokens;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
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
class PddCostPlanControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired TokenIssuer tokens;
    @org.junit.jupiter.api.BeforeEach void resetTestPlans(){
        assertThat(jdbc.queryForObject("select database()",String.class)).endsWith("_test");
        jdbc.update("delete from pdd_sku_cost_plan");
    }
    private String token(String... permissions){return "Bearer "+TestAuthTokens.issue(jdbc,tokens,"13900000021",permissions);}
    private Map<String,Object> line(){
        var value=new HashMap<String,Object>(Map.of("specification","透明350ml","skuCode","","unitCost",2.1,
                "shippingCost",2.05,"packagingCost",0,"otherCost",0,"salePrice",5.5,"refundRate",13,"platformFeeRate",0,"adMode","roi"));
        value.put("roi",5);return value;
    }
    private String input(String name,long version,Map<String,Object> line) throws Exception{
        return mapper.writeValueAsString(Map.of("name",name,"remark","接口验证","version",version,"lines",List.of(line)));
    }
    @Test void persistsReloadsAndUsesVersionChecksToProtectConcurrentEdits() throws Exception{
        var auth=token("product:view","product:edit");
        var response=mvc.perform(post("/api/product-cost-plans").header("Authorization",auth).contentType(APPLICATION_JSON)
                        .content(input("成本保存测试",0,line())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.lines[0].result.netProfit").value(.07))
                .andExpect(jsonPath("$.data.lines[0].result.grossMargin").value(24.5455)).andReturn();
        long id=mapper.readTree(response.getResponse().getContentAsString()).path("data").path("id").asLong();
        mvc.perform(get("/api/product-cost-plans/{id}",id).header("Authorization",auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1)).andExpect(jsonPath("$.data.lines[0].input.unitCost").value(2.1));
        var changed=line();changed.put("salePrice",10);
        mvc.perform(put("/api/product-cost-plans/{id}",id).header("Authorization",auth).contentType(APPLICATION_JSON)
                .content(input("成本修改测试",1,changed))).andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(2));
        mvc.perform(put("/api/product-cost-plans/{id}",id).header("Authorization",auth).contentType(APPLICATION_JSON)
                .content(input("旧版本不应覆盖",1,line()))).andExpect(status().isConflict());
        mvc.perform(get("/api/product-cost-plans").header("Authorization",auth).param("keyword","成本修改测试"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        assertThat(jdbc.queryForObject("select plan_name from pdd_sku_cost_plan where id=?",String.class,id)).isEqualTo("成本修改测试");
    }
    @Test void archivesAndRestoresSavedInputs() throws Exception{
        var auth=token("product:view","product:edit");
        var response=mvc.perform(post("/api/product-cost-plans").header("Authorization",auth).contentType(APPLICATION_JSON)
                .content(input("归档测试",0,line()))).andExpect(status().isOk()).andReturn();
        long id=mapper.readTree(response.getResponse().getContentAsString()).path("data").path("id").asLong();
        mvc.perform(post("/api/product-cost-plans/{id}/archive",id).param("version","1").header("Authorization",auth)).andExpect(status().isOk());
        mvc.perform(get("/api/product-cost-plans").param("keyword","归档测试").header("Authorization",auth)).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/product-cost-plans").param("keyword","归档测试").param("archived","true").header("Authorization",auth)).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(put("/api/product-cost-plans/{id}",id).header("Authorization",auth).contentType(APPLICATION_JSON)
                .content(input("不可编辑归档",2,line()))).andExpect(status().isConflict());
        mvc.perform(post("/api/product-cost-plans/{id}/restore",id).param("version","2").header("Authorization",auth)).andExpect(status().isOk());
        mvc.perform(get("/api/product-cost-plans/{id}",id).header("Authorization",auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.archived").value(false)).andExpect(jsonPath("$.data.lines[0].input.salePrice").value(5.5));
    }
    @Test void validatesInputsAndEnforcesReadAndWritePermissions() throws Exception{
        var viewer=token("product:view");var editor=token("product:view","product:edit");
        mvc.perform(post("/api/product-cost-plans").header("Authorization",viewer).contentType(APPLICATION_JSON)
                .content(input("无权限",0,line()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/product-cost-plans").header("Authorization",token("customer:view"))).andExpect(status().isForbidden());
        var invalid=line();invalid.put("roi",0);
        mvc.perform(post("/api/product-cost-plans").header("Authorization",editor).contentType(APPLICATION_JSON)
                .content(input("错误ROI",0,invalid))).andExpect(status().isBadRequest());
        invalid=line();invalid.put("skuId",Long.MAX_VALUE);
        mvc.perform(post("/api/product-cost-plans").header("Authorization",editor).contentType(APPLICATION_JSON)
                .content(input("错误SKU",0,invalid))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/product-cost-plans").param("size","201").header("Authorization",viewer)).andExpect(status().isBadRequest());
    }
    @Test void importsEnabledSkuAndSavesCostSnapshotsWithoutChangingCatalogOrStock() throws Exception{
        var auth=token("product:view","product:edit");
        var suffix=java.util.UUID.randomUUID().toString().substring(0,8);
        var code="PDD-COST-"+suffix;
        jdbc.update("insert into product_category(category_code,category_name,status,created_at,updated_at) values(?,?,'enabled',now(3),now(3))",code,"成本测试分类"+suffix);
        var categoryId=jdbc.queryForObject("select id from product_category where category_code=?",Long.class,code);
        jdbc.update("insert into product_spu(product_code,item_no,product_name,category_id,product_type,status,created_at,updated_at) values(?,?,?,?,'simple','enabled',now(3),now(3))",code,code,"成本测试商品"+suffix,categoryId);
        var productId=jdbc.queryForObject("select id from product_spu where product_code=?",Long.class,code);
        jdbc.update("insert into product_sku(product_id,sku_code,sku_name,sales_unit,standard_cost,default_sale_price,is_default,status,created_at,updated_at) values(?,?,'透明','只',2.10,5.50,true,'enabled',now(3),now(3))",productId,code);
        var skuId=jdbc.queryForObject("select id from product_sku where sku_code=?",Long.class,code);
        var stockRows=jdbc.queryForList("select * from inventory_balance where sku_id=?",skuId);
        mvc.perform(get("/api/product-cost-plans/sku-options").param("keyword",code).header("Authorization",auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].skuId").value(skuId))
                .andExpect(jsonPath("$.data[0].unitCost").value(2.1)).andExpect(jsonPath("$.data[0].salePrice").value(5.5));
        var linked=line();linked.put("skuId",skuId);linked.put("skuCode","untrusted-code");linked.put("unitCost",3.25);
        mvc.perform(post("/api/product-cost-plans").header("Authorization",auth).contentType(APPLICATION_JSON)
                .content(input("关联成本测试"+suffix,0,linked))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lines[0].input.skuCode").value(code)).andExpect(jsonPath("$.data.lines[0].input.unitCost").value(3.25));
        assertThat(jdbc.queryForObject("select standard_cost from product_sku where id=?",java.math.BigDecimal.class,skuId)).isEqualByComparingTo("2.10");
        assertThat(jdbc.queryForObject("select default_sale_price from product_sku where id=?",java.math.BigDecimal.class,skuId)).isEqualByComparingTo("5.50");
        assertThat(jdbc.queryForList("select * from inventory_balance where sku_id=?",skuId)).isEqualTo(stockRows);
        jdbc.update("update product_sku set status='disabled' where id=?",skuId);
        mvc.perform(get("/api/product-cost-plans/sku-options").param("keyword",code).header("Authorization",auth)).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(post("/api/product-cost-plans").header("Authorization",auth).contentType(APPLICATION_JSON)
                .content(input("停用SKU不能关联",0,linked))).andExpect(status().isBadRequest());
    }
}
