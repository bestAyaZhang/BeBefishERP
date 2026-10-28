package com.bebefish.erp.logistics;

import static com.bebefish.erp.logistics.LogisticsDtos.*;
import static org.assertj.core.api.Assertions.*;
import com.bebefish.erp.logistics.pricing.FreightRules.*;
import com.bebefish.erp.shipping.domain.*;
import com.bebefish.erp.shipping.logistics.yimi.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties="ERP_LOGISTICS_MASTER_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@ActiveProfiles("test") @Transactional
class YimiPickupAssignmentTest {
    @Autowired LogisticsCatalogService catalog;
    @Autowired FreightQuoteService quotes;
    @Autowired ObjectMapper mapper;

    @Test void keepsLeadingZeroAndUpdatesPickupWithoutChangingIdentityOrDroppingKeys() {
        var company=company();
        var saved=catalog.saveAccount(company.id(),account(0,"INERT-KEY","INERT-SECRET",false,"00012345","验收业务员"));
        assertThat(saved.ready()).isTrue();
        assertThat(mapper.valueToTree(saved).path("yimi").path("designatedPickup").asBoolean()).isTrue();
        assertThat(mapper.valueToTree(saved).path("yimi").path("salesmanCode").asText()).isEqualTo("00012345");
        var updated=catalog.saveAccount(company.id(),account(saved.version(),"","",true,"00012346","另一业务员"));
        assertThat(updated.id()).isEqualTo(saved.id());
        assertThat(updated.ready()).isTrue();
        var resolved=catalog.resolveVersion(updated.accountVersionId()).config();
        assertThat(resolved.appKey()).isEqualTo("INERT-KEY");
        assertThat(resolved.appSecret()).isEqualTo("INERT-SECRET");
        assertThat(mapper.valueToTree(resolved).path("yimi").path("salesmanName").asText()).isEqualTo("另一业务员");
        assertThat(mapper.valueToTree(catalog.resolveVersion(saved.accountVersionId()).config()).path("yimi").path("salesmanCode").asText()).isEqualTo("00012345");
    }
    @Test void incompleteDesignatedPickupCanSaveAndEstimateButCannotApiOrder() {
        for(var missing:List.of(List.of("00012345",""),List.of("","业务员"),List.of("",""))) {
            var company=company();var saved=catalog.saveAccount(company.id(),account(0,"INERT-KEY","INERT-SECRET",true,missing.get(0),missing.get(1)));
            assertThat(saved.ready()).isFalse();
            var rules=new Rules("weight",false,null,List.of(new Band(BigDecimal.ZERO,null,BigDecimal.ONE,BigDecimal.TEN,BigDecimal.ONE,BigDecimal.ONE)),List.of());
            catalog.createTemplate(company.id(),new TemplateInput("验收费率","1","港到门","浙江省","金华市","东阳市",rules,"enabled","",0));
            var input=new QuoteInput("浙江省","金华市","东阳市","广东省","东莞市","寮步镇",BigDecimal.TEN,null,BigDecimal.ONE,180,102);
            var quote=quotes.quote(input,77).quotes().stream().filter(q->q.companyId()==company.id()).findFirst().orElseThrow();
            assertThat(quote.available()).isTrue();assertThat(quote.apiReady()).isFalse();
        }
    }
    @Test void ordinaryYimiAccountWithoutDesignatedPickupKeepsExistingReadiness() {
        var saved=catalog.saveAccount(company().id(),account(0,"INERT-KEY","INERT-SECRET",false,"",""));
        assertThat(saved.ready()).isTrue();
        assertThat(mapper.valueToTree(saved).path("yimi").path("designatedPickup").asBoolean()).isFalse();
    }
    @Test void rejectsOversizedPickupValuesInsteadOfSilentlyTruncating() {
        for(var fields:List.of(List.of("x".repeat(51),"业务员"),List.of("00012345","名".repeat(51)))) {
            var company=company();
            assertThatThrownBy(()->catalog.saveAccount(company.id(),account(0,"INERT-KEY","INERT-SECRET",true,fields.get(0),fields.get(1))))
                .isInstanceOf(com.bebefish.erp.common.api.BusinessException.class);
        }
    }
    @Test void emitsPickupFieldsAtRootAndKeepsInternalFlagOutOfCarrierBody() {
        var config=properties(true,"00012345","验收业务员");
        var body=YimiPayloads.orderParams(content(),"BF-PICKUP-1",config,"1");
        assertThat(body).containsEntry("salesmanCode","00012345").containsEntry("salesmanName","验收业务员");
        assertThat(body).doesNotContainKeys("designatedPickup","salesmanPhone","appKey","appSecret");
        assertThat(mapper.valueToTree(body).path("settlementType").isTextual()).isTrue();
        assertThat(body.get("settlementType")).isEqualTo("1");
    }
    @Test void newPickupConfigDoesNotBlockHistoricalCredentialUseButBlocksNewOrder() {
        var config=properties(true,"00012345","");
        assertThat(config.ready()).isTrue();
        assertThatThrownBy(()->YimiPayloads.orderParams(content(),"BF-PICKUP-1",config,"1")).isInstanceOf(IllegalArgumentException.class);
    }
    private Company company(){String code="PICKUP-"+java.util.UUID.randomUUID().toString().substring(0,12);return catalog.createCompany(new CompanyInput(code,code,"YIMI","enabled","","",0,"",null));}
    private AccountInput account(long version,String key,String secret,boolean designated,String code,String name) {
        return mapper.convertValue(Map.of("version",version,"appKey",key,"appSecret",secret,
            "sender",Map.of("name","验收寄件人","phone","13800000001","province","浙江省","city","金华市","county","东阳市","address","验收仓库1号"),
            "yimi",Map.of("sellerId","PICKUP-SELLER","subscriptionId","PICKUP-SUB","designatedPickup",designated,"salesmanCode",code,"salesmanName",name)),AccountInput.class);
    }
    private YimiProperties properties(boolean designated,String code,String name) {
        return mapper.convertValue(Map.of("testEnvironment",true,"appKey","INERT-KEY","appSecret","INERT-SECRET","sellerId","PICKUP-SELLER","subscriptionId","PICKUP-SUB",
            "designatedPickup",designated,"salesmanCode",code,"salesmanName",name),YimiProperties.class);
    }
    private ShipmentContent content() {
        var form=new ShipmentFormInput("验收平台","验收店",List.of("验收备货人"),"验收收件人","13800000002","广东省","东莞市","寮步镇","验收路36号","验收商品1件","",null,
            new AneOrderDraft("验收商品","纸箱",BigDecimal.ONE,new BigDecimal("0.01"),1,524,180,102,"接口测试",new YimiOrderOptions(0,null,"")),
            "验收寄件人","13800000001","浙江省","金华市","东阳市","验收仓库1号");
        return ShipmentContent.from(LocalDate.of(2026,10,28),form,"unfinished","验收录入人","","");
    }
}
