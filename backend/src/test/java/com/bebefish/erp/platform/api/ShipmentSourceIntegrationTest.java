package com.bebefish.erp.platform.api;
import com.bebefish.erp.auth.domain.TokenIssuer;
import com.bebefish.erp.support.TestAuthTokens;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class ShipmentSourceIntegrationTest {
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired TokenIssuer tokens; @Autowired ObjectMapper mapper;
    @Autowired com.bebefish.erp.shipping.domain.ShipmentRepository shipments;
    String token() { return TestAuthTokens.issue(jdbc,tokens,"13900008800","platform:view","platform:create","platform:edit","shipping:view","shipping:create","shipping:edit"); }
    JsonNode postData(String path,JsonNode body,String token) throws Exception {
        return mapper.readTree(mvc.perform(post(path).header("Authorization","Bearer "+token).contentType(APPLICATION_JSON).content(body.toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).get("data");
    }
    JsonNode platform(String code,String t) throws Exception { return postData("/api/platforms",mapper.readTree("{\"code\":\""+code+"\",\"name\":\""+code+"平台\"}"),t); }
    JsonNode shop(long id,String code,String t) throws Exception { return postData("/api/platform-shops",mapper.readTree("{\"platformId\":"+id+",\"code\":\""+code+"\",\"name\":\""+code+"店\",\"channelType\":\"ecommerce\",\"ownerName\":\"测试运营\",\"optionLabel\":\""+code+"选项\"}"),t); }
    ObjectNode form() throws Exception { return (ObjectNode)mapper.readTree("""
      {"platform":"伪造平台","shopName":"伪造店铺","preparers":["备货人"],"recipientName":"收件人","recipientPhone":"13800000000",
       "recipientProvince":"浙江省","recipientCity":"杭州市","recipientCounty":"余杭区","recipientDetailAddress":"测试路1号",
       "preparationContent":"测试货物","remark":"","estimatedFreight":null,
       "orderDraft":{"cargoName":"货物","packType":"纸箱","weight":null,"volume":null,"pieceAmount":1,"productTypeId":524,"goodsType":180,"payType":102,"logisticsRemark":""}}
       """); }
    @Test void createUsesCatalogSnapshotAndRenameOrDisablePreservesHistory() throws Exception {
        String t=token(); var p=platform("SOURCE_P",t); var s=shop(p.get("id").asLong(),"SOURCE_S",t);
        var f=form().put("platformId",p.get("id").asLong()).put("shopId",s.get("id").asLong());
        var saved=postData("/api/shipments",mapper.createObjectNode().set("form",f),t);
        assertThat(saved.at("/content/platform").asText()).isEqualTo("SOURCE_P平台");
        assertThat(saved.at("/content/shopName").asText()).isEqualTo("SOURCE_S店");
        long id=saved.get("id").asLong();
        jdbc.update("update sales_platform set platform_name='已改名',status='disabled' where id=?",p.get("id").asLong());
        var old=(ObjectNode)saved.get("content"); old.put("remark","仍可编辑");
        mvc.perform(put("/api/shipments/"+id).header("Authorization","Bearer "+t).contentType(APPLICATION_JSON)
            .content(mapper.createObjectNode().put("version",0).put("status","partially_shipped").set("form",old).toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.content.platform").value("SOURCE_P平台"));
        mvc.perform(post("/api/shipments").header("Authorization","Bearer "+t).contentType(APPLICATION_JSON).content(mapper.createObjectNode().set("form",f).toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLATFORM_DISABLED"));
        mvc.perform(get("/api/shipments?platformId="+p.get("id")).header("Authorization","Bearer "+t)).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
    }
    @Test void rejectsMissingIdsAndCrossPlatformShopAndSeparatesOptionsPermissions() throws Exception {
        String t=token(); var a=platform("SOURCE_A",t); var b=platform("SOURCE_B",t); var s=shop(b.get("id").asLong(),"SOURCE_BS",t);
        var f=form();
        mvc.perform(post("/api/shipments").header("Authorization","Bearer "+t).contentType(APPLICATION_JSON).content(mapper.createObjectNode().set("form",f).toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SHIPMENT_SOURCE_SELECTION_REQUIRED"));
        f.put("platformId",a.get("id").asLong()).put("shopId",s.get("id").asLong());
        mvc.perform(post("/api/shipments").header("Authorization","Bearer "+t).contentType(APPLICATION_JSON).content(mapper.createObjectNode().set("form",f).toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PLATFORM_SHOP_MISMATCH"));
        String read=TestAuthTokens.issue(jdbc,tokens,"13900008801","shipping:view");
        mvc.perform(get("/api/shipments/form-options").header("Authorization","Bearer "+read)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.shops[0].id").exists()).andExpect(jsonPath("$.data.shops[0].remark").doesNotExist());
        mvc.perform(get("/api/platform-shops").header("Authorization","Bearer "+read)).andExpect(status().isForbidden());
    }
    @Test void orderedShipmentCannotChangeIdsEvenWhenSnapshotNamesAreIdentical() throws Exception {
        String t=token(); var p=platform("LOCK_P",t);
        var a=shop(p.get("id").asLong(),"LOCK_A",t); var b=shop(p.get("id").asLong(),"LOCK_B",t);
        var f=form().put("platformId",p.get("id").asLong()).put("shopId",a.get("id").asLong());
        var json=postData("/api/shipments",mapper.createObjectNode().set("form",f),t);
        long id=json.get("id").asLong();
        jdbc.update("""
            insert into shipment_logistics_order(shipment_id,order_no,state,test_environment,tracking_no,child_tracking_nos,
            message,request_snapshot,operator_id,created_at,updated_at)
            values(?,'LOCK-ORDER','unknown',false,'','','','{}','test',now(3),now(3))
            """,id);
        var original=shipments.findById(id).orElseThrow();
        var source=new com.bebefish.erp.shipping.domain.ShipmentSource(p.get("id").asLong(),b.get("id").asLong(),original.content().platform(),original.content().shopName());
        var input=original.content().form().withSource(source);
        var content=com.bebefish.erp.shipping.domain.ShipmentContent.from(original.content().shipmentDate(),input,"unfinished","test","","");
        var changed=new com.bebefish.erp.shipping.domain.Shipment(id,original.shipmentNo(),content,1,"test","test",original.createdAt(),original.updatedAt());
        assertThat(shipments.update(changed,0)).isFalse();
        f.put("shopId",b.get("id").asLong());
        mvc.perform(put("/api/shipments/"+id).header("Authorization","Bearer "+t).contentType(APPLICATION_JSON)
            .content(mapper.createObjectNode().put("version",0).put("status","unfinished").set("form",f).toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SHIPMENT_ALREADY_ORDERED"));
    }
}
