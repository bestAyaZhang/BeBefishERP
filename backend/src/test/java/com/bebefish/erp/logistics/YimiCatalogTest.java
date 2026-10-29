package com.bebefish.erp.logistics;

import static com.bebefish.erp.logistics.LogisticsDtos.*;
import static org.assertj.core.api.Assertions.*;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.logistics.pricing.FreightRules.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** Real catalog/pricing/DB coverage. Credentials here are inert fixtures; no carrier requests. */
@SpringBootTest(properties="ERP_LOGISTICS_MASTER_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@ActiveProfiles("test") @Transactional
class YimiCatalogTest {
    @Autowired LogisticsCatalogService catalog;
    @Autowired FreightQuoteService quotes;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    Company company(String code) {
        return catalog.createCompany(new CompanyInput(code,code,"YIMI","enabled","","",0,"",null));
    }
    AccountInput account(long revision,String app,String secret,String seller,String subscription) throws Exception {
        return mapper.readValue("""
            {"version":%d,"appKey":"%s","appSecret":"%s","yimi":{"sellerId":"%s","subscriptionId":"%s"},
             "sender":{"name":"仓库","phone":"13800138000","province":"浙江省","city":"杭州市","county":"余杭区","address":"仓库路1号"}}
            """.formatted(revision,app,secret,seller,subscription),AccountInput.class);
    }
    QuoteInput input(int goodsType,String options) throws Exception {
        return mapper.readValue("""
            {"senderProvince":"浙江省","senderCity":"杭州市","senderCounty":"余杭区","recipientProvince":"江苏省","recipientCity":"南京市","recipientCounty":"江宁区",
             "weight":18.5,"volume":0.1,"pieceAmount":1,"goodsType":%d,"payType":1,"yimi":%s}
            """.formatted(goodsType,options),QuoteInput.class);
    }
    Template template(Company c,String product) {
        Rules rules=new Rules("weight",false,null,List.of(new Band(BigDecimal.ZERO,null,new BigDecimal("20"),new BigDecimal("12"),BigDecimal.ONE,BigDecimal.ONE)),List.of());
        return catalog.createTemplate(c.id(),new TemplateInput("壹米产品"+product,product,"产品"+product,"浙江省","杭州市","余杭区",rules,"enabled","",0));
    }
    List<Quote> companyQuotes(Company c,QuoteInput in) {
        return quotes.quote(in,77).quotes().stream().filter(q->q.companyId()==c.id()).toList();
    }
    @Test void storesYimiSecretsEncryptedAndPublicViewsExposeOnlyConfigurationFlags() throws Exception {
        Company c=company("YIMI_ENCRYPTED");
        AccountView first=catalog.saveAccount(c.id(),account(0,"INERT-APP-KEY","INERT-APP-SECRET","SELLER-A","SUB-A"));
        assertThat(first.ready()).isTrue();
        String view=mapper.writeValueAsString(first);
        assertThat(mapper.readTree(view).path("appSecretConfigured").asBoolean()).isTrue();
        assertThat(mapper.readTree(view).path("yimi").path("sellerId").asText()).isEqualTo("SELLER-A");
        assertThat(view).doesNotContain("INERT-APP-KEY","INERT-APP-SECRET","\"appKey\":","\"appSecret\":");
        String publicJson=jdbc.queryForObject("select config_json from logistics_account_version where id=?",String.class,first.accountVersionId());
        String cipher=jdbc.queryForObject("select secret_cipher from logistics_account_version where id=?",String.class,first.accountVersionId());
        assertThat(publicJson).doesNotContain("INERT-APP-KEY","INERT-APP-SECRET");
        assertThat(cipher).isNotNull().doesNotContain("INERT-APP-KEY","INERT-APP-SECRET");
        assertThat(mapper.valueToTree(catalog.resolveVersion(first.accountVersionId()).config()).path("appSecret").asText()).isEqualTo("INERT-APP-SECRET");
    }
    @Test void yimiConfigurationAuditRetainsActorAndVersionsWithoutSecretOrBusinessPayloads() throws Exception {
        Company c=company("YIMI_AUDIT");
        var actor=new com.bebefish.erp.common.security.ErpPrincipal(77,"","壹米审计",List.of(),List.of());
        AccountView saved=catalog.saveAccount(c.id(),account(0,"AUDIT-KEY","AUDIT-SECRET","AUDIT-SELLER","AUDIT-SUB"),actor);
        catalog.account(c.id(),actor); catalog.accounts(c.id(),actor);
        var entries=jdbc.queryForList("select * from logistics_audit where company_id=? order by id",c.id());
        assertThat(entries).hasSize(3);
        assertThat(entries).allSatisfy(row->assertThat(((Number)row.get("actor_employee_id")).longValue()).isEqualTo(77));
        assertThat(((Number)entries.getFirst().get("account_version_id")).longValue()).isEqualTo(saved.accountVersionId());
        assertThat(entries.toString()).doesNotContain("AUDIT-KEY","AUDIT-SECRET","AUDIT-SELLER","AUDIT-SUB","appSecret","appKey");
    }
    @Test void blankSecretsPreserveOnlyCurrentIdentityAndSecretRotationDoesNotChangeAccount() throws Exception {
        Company c=company("YIMI_ROTATE");
        AccountView first=catalog.saveAccount(c.id(),account(0,"APP-A","SECRET-A","SELLER-A","SUB-A"));
        AccountView preserved=catalog.saveAccount(c.id(),account(1,"","","SELLER-A","SUB-A"));
        assertThat(preserved.id()).isEqualTo(first.id());
        assertThat(catalog.resolveVersion(preserved.accountVersionId()).config().appKey()).isEqualTo("APP-A");
        AccountView rotated=catalog.saveAccount(c.id(),account(2,"","SECRET-B","SELLER-A","SUB-A"));
        assertThat(rotated.id()).isEqualTo(first.id());
        assertThat(mapper.valueToTree(catalog.resolveVersion(rotated.accountVersionId()).config()).path("appSecret").asText()).isEqualTo("SECRET-B");
        assertThat(mapper.valueToTree(catalog.resolveVersion(first.accountVersionId()).config()).path("appSecret").asText()).isEqualTo("SECRET-A");
        assertThatThrownBy(()->catalog.saveAccount(c.id(),account(2,"","SECRET-C","SELLER-A","SUB-A"))).isInstanceOf(BusinessException.class);
    }
    @Test void changingAppSellerOrSubscriptionCreatesNewIdentityWithoutSecretCarryover() throws Exception {
        Company c=company("YIMI_IDENTITY");
        AccountView first=catalog.saveAccount(c.id(),account(0,"APP-A","SECRET-A","SELLER-A","SUB-A"));
        AccountView changedSeller=catalog.saveAccount(c.id(),account(1,"","","SELLER-B","SUB-A"));
        assertThat(changedSeller.id()).isNotEqualTo(first.id());
        assertThat(changedSeller.appKeyConfigured()).isFalse();
        assertThat(mapper.valueToTree(changedSeller).path("appSecretConfigured").asBoolean()).isFalse();
        assertThat(changedSeller.ready()).isFalse();
        AccountView configured=catalog.saveAccount(c.id(),account(2,"APP-A","SECRET-B","SELLER-B","SUB-A"));
        AccountView changedSubscription=catalog.saveAccount(c.id(),account(3,"APP-A","","SELLER-B","SUB-B"));
        assertThat(changedSubscription.id()).isNotEqualTo(configured.id());
        assertThat(mapper.valueToTree(changedSubscription).path("appSecretConfigured").asBoolean()).isFalse();
        AccountView changedApp=catalog.saveAccount(c.id(),account(4,"APP-B","","SELLER-B","SUB-B"));
        assertThat(changedApp.id()).isNotEqualTo(changedSubscription.id());
        assertThat(mapper.valueToTree(changedApp).path("appSecretConfigured").asBoolean()).isFalse();
        // Returning to an older identity must still require explicitly supplied secrets.
        AccountView returned=catalog.saveAccount(c.id(),account(5,"APP-A","","SELLER-A","SUB-A"));
        assertThat(returned.id()).isEqualTo(first.id());
        assertThat(returned.ready()).isFalse();
        assertThat(mapper.valueToTree(returned).path("appSecretConfigured").asBoolean()).isFalse();
        assertThat(mapper.valueToTree(catalog.resolveVersion(first.accountVersionId()).config()).path("appSecret").asText()).isEqualTo("SECRET-A");
    }
    @Test void pinnedYimiGatewayRejectsEnvironmentChangesAndOtherUrlComponents() throws Exception {
        Company c=company("YIMI_URLS");
        for(String url:List.of("https://yh-uop.uce.cn/uce-uop-main/api/gateway","http://yh-uop-test.yimidida.com/uce-uop-main/api/gateway", "https://yh-uop-test.yimidida.com:443/uce-uop-main/api/gateway", "https://yh-uop-test.yimidida.com/uce-uop-main/api/gateway?x=1", "https://yh-uop-test.yimidida.com/uce-uop-main/api/gateway#x","https://evil.test/uce-uop-main/api/gateway")) {
            var node=mapper.valueToTree(account(0,"APP","SECRET","SELLER","SUB"));
            ((com.fasterxml.jackson.databind.node.ObjectNode)node).put("orderUrl",url);
            AccountInput in=mapper.treeToValue(node,AccountInput.class);
            assertThatThrownBy(()->catalog.saveAccount(c.id(),in)).isInstanceOf(BusinessException.class).hasMessageContaining("固定HTTPS");
        }
        AccountView test=catalog.saveAccount(c.id(),account(0,"APP","SECRET","SELLER","SUB"));
        assertThat(test.orderUrl()).isEqualTo("https://yh-uop-test.yimidida.com/uce-uop-main/api/gateway");
        MockEnvironment environment=new MockEnvironment(); environment.setActiveProfiles("prod");
        LogisticsCatalogService prod=new LogisticsCatalogService(jdbc,mapper,new LogisticsSecretCipher("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="),Clock.systemUTC(),environment);
        AccountView production=prod.saveAccount(c.id(),account(0,"APP","SECRET","SELLER","SUB"));
        assertThat(production.id()).isNotEqualTo(test.id());
        assertThat(production.orderUrl()).isEqualTo("https://yh-uop.uce.cn/uce-uop-main/api/gateway");
        assertThatThrownBy(()->prod.resolveVersion(test.accountVersionId())).isInstanceOf(BusinessException.class).hasMessageContaining("运行环境");
    }
    @Test void missingBusinessIdsOrEncryptionKeyBlockApiButKeepOfflinePrices() throws Exception {
        Company c=company("YIMI_DRAFT");
        AccountView incomplete=catalog.saveAccount(c.id(),account(0,"APP","SECRET","",""));
        assertThat(incomplete.ready()).isFalse(); template(c,"1");
        Quote q=companyQuotes(c,input(180,"null")).getFirst();
        assertThat(q.available()).isTrue(); assertThat(q.apiReady()).isFalse();
        LogisticsCatalogService noKey=new LogisticsCatalogService(jdbc,mapper,new LogisticsSecretCipher(""),Clock.systemUTC(),new MockEnvironment());
        assertThat(noKey.account(c.id()).ready()).isFalse();
        assertThatThrownBy(()->noKey.saveAccount(c.id(),account(1,"APP","NEW","SELLER","SUB"))).isInstanceOf(BusinessException.class).hasMessageContaining("主密钥");
    }
    @Test void secretlessDraftCanBeEditedWithoutAMasterKeyAndManualCannotStoreNewSecretType() throws Exception {
        Company c=company("YIMI_NO_KEY");
        LogisticsCatalogService noKey=new LogisticsCatalogService(jdbc,mapper,new LogisticsSecretCipher(""),Clock.systemUTC(),new MockEnvironment());
        AccountView first=noKey.saveAccount(c.id(),account(0,"","","SELLER","SUB"));
        AccountView second=noKey.saveAccount(c.id(),account(1,"","","SELLER","SUB"));
        assertThat(second.id()).isEqualTo(first.id()); assertThat(second.ready()).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from logistics_account_version where account_id=? and secret_cipher is not null",Long.class,first.id())).isZero();
        Company manual=catalog.createCompany(new CompanyInput("YIMI_MANUAL_SECRET","线下","MANUAL","enabled","","",0,"",null));
        assertThatThrownBy(()->catalog.saveAccount(manual.id(),account(0,"","SECRET","",""))).isInstanceOf(BusinessException.class);
    }
    @Test void tamperedPublicConfigOrCipherBlocksApiReadinessWithoutLosingOfflinePricing() throws Exception {
        Company c=company("YIMI_TAMPER");
        AccountView account=catalog.saveAccount(c.id(),account(0,"APP","SECRET","SELLER","SUB")); template(c,"1");
        jdbc.update("update logistics_account_version set config_json=json_set(config_json,'$.yimi.sellerId','OTHER') where id=?",account.accountVersionId());
        assertThat(catalog.account(c.id()).ready()).isFalse();
        Quote q=companyQuotes(c,input(180,"null")).getFirst();
        assertThat(q.available()).isTrue(); assertThat(q.apiReady()).isFalse();
        // An explicit update restores the current identity from the authenticated immutable version.
        AccountView updated=catalog.saveAccount(c.id(),account(1,"APP","NEW-SECRET","SELLER","SUB"));
        assertThat(updated.ready()).isTrue();
        jdbc.update("update logistics_account_version set secret_cipher='invalid-cipher' where id=?",updated.accountVersionId());
        assertThat(catalog.account(c.id()).ready()).isFalse();
        assertThat(companyQuotes(c,input(180,"null")).getFirst().available()).isTrue();
    }
    @Test void readyYimiQuotesRespectProductDeliverySemanticsAndOptionsInvalidateIssuedQuotes() throws Exception {
        Company c=company("YIMI_QUOTE"); catalog.saveAccount(c.id(),account(0,"APP","SECRET","SELLER","SUB"));
        template(c,"1"); template(c,"2");
        QuoteInput upstairs=input(179,"{\"visitDelivery\":0,\"deliveryFlag\":3,\"floorNo\":\"2\"}");
        List<Quote> results=companyQuotes(c,upstairs);
        Quote q=results.stream().filter(r->r.productCode().equals("1")).findFirst().orElseThrow();
        assertThat(q.available()).isTrue(); assertThat(q.apiReady()).isTrue();
        assertThat(results.stream().filter(r->r.productCode().equals("2")).findFirst().orElseThrow().available()).isFalse();
        List<Quote> pickup=companyQuotes(c,input(285,"{\"visitDelivery\":1}"));
        assertThat(pickup.stream().filter(r->r.productCode().equals("2")).findFirst().orElseThrow().available()).isTrue();
        assertThat(pickup.stream().filter(r->r.productCode().equals("1")).findFirst().orElseThrow().available()).isFalse();
        assertThat(quotes.getValid(q.quoteId(),77,upstairs).quoteId()).isEqualTo(q.quoteId());
        assertThatThrownBy(()->quotes.getValid(q.quoteId(),77,input(179,"{\"visitDelivery\":1,\"deliveryFlag\":3,\"floorNo\":\"2\"}"))).isInstanceOf(BusinessException.class).hasMessageContaining("条件");
        assertThatThrownBy(()->quotes.getValid(q.quoteId(),77,input(179,"{\"visitDelivery\":0,\"deliveryFlag\":2}"))).isInstanceOf(BusinessException.class).hasMessageContaining("条件");
        assertThatThrownBy(()->quotes.getValid(q.quoteId(),77,input(179,"{\"visitDelivery\":0,\"deliveryFlag\":3,\"floorNo\":\"3\"}"))).isInstanceOf(BusinessException.class).hasMessageContaining("条件");
    }
    @Test void canonicalEmptyOptionsMatchHistoricalQuotesButExplicitOptionsParticipate() throws Exception {
        QuoteInput empty=input(180,"{\"visitDelivery\":null,\"deliveryFlag\":null,\"floorNo\":\"   \"}");
        QuoteInput historical=input(180,"null");
        assertThat(FreightQuoteService.canonical(empty)).isEqualTo(FreightQuoteService.canonical(historical));
        assertThat(FreightQuoteService.canonical(input(180,"{\"visitDelivery\":0}"))).isNotEqualTo(FreightQuoteService.canonical(historical));
    }
    @Test void shipmentQuoteConditionsIncludeExplicitYimiOptions() throws Exception {
        var form=mapper.readValue("""
            {"orderDraft":{"goodsType":179,"yimi":{"visitDelivery":1,"deliveryFlag":3,"floorNo":" 2 "}}}
            """,com.bebefish.erp.shipping.domain.ShipmentFormInput.class);
        var condition=mapper.valueToTree(FreightQuoteService.input(form));
        assertThat(condition.path("yimi").path("visitDelivery").asInt(-1)).isEqualTo(1);
        assertThat(condition.path("yimi").path("deliveryFlag").asInt(-1)).isEqualTo(3);
        assertThat(condition.path("yimi").path("floorNo").asText()).isEqualTo("2");
    }
}
