package com.bebefish.erp.logistics;

import static org.assertj.core.api.Assertions.*;
import static com.bebefish.erp.logistics.LogisticsDtos.*;
import static com.bebefish.erp.logistics.LogisticsPersistenceTest.input;
import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.logistics.pricing.FreightRules.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties="ERP_LOGISTICS_MASTER_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=") @ActiveProfiles("test") @Transactional
class FreightQuoteServiceTest {
    @Autowired LogisticsCatalogService catalog; @Autowired JdbcTemplate jdbc; @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    private final Instant issued=Instant.parse("2026-10-26T04:00:00Z");
    FreightQuoteService service(Instant now) { return new FreightQuoteService(jdbc,catalog,Clock.fixed(now,ZoneOffset.UTC)); }
    Company setup(String code,String fee) {
        Company c=catalog.createCompany(new CompanyInput(code,code,"MANUAL","enabled","","",0,"",null));
        if(fee!=null) catalog.createTemplate(c.id(),new TemplateInput("价格", "P","标准快运","330000","杭州市","余杭区",rules(fee),"enabled","",0));
        return c;
    }
    Rules rules(String fee) { return new Rules("weight",false,null,List.of(new Band(BigDecimal.ZERO,null,new BigDecimal("20"),new BigDecimal(fee),BigDecimal.ONE,BigDecimal.ONE)),List.of()); }
    Quote quote(Company c) { return service(issued).quote(input("18.500"),77).quotes().stream().filter(q->q.companyId()==c.id()).findFirst().orElseThrow(); }
    @Test void recommendsLowestValidOfflinePriceAndBreaksTiesByStableCatalogOrder() {
        Company a=setup("FIRST","10"), b=setup("SECOND","10"),bad=setup("NO_TEMPLATE",null);
        QuoteBatch batch=service(issued).quote(input("18.5"),77);
        List<Quote> ours=batch.quotes().stream().filter(q->List.of(a.id(),b.id(),bad.id()).contains(q.companyId())).toList();
        assertThat(ours).hasSize(3); assertThat(ours.get(0).recommended()).isTrue(); assertThat(ours.get(1).recommended()).isFalse();
        assertThat(ours.get(0).apiReady()).isFalse(); assertThat(ours.get(0).amount()).isEqualByComparingTo("10");
        assertThat(ours.get(2).available()).isFalse(); assertThat(ours.get(2).reason()).contains("未配置");
        assertThat(batch.legacyOrderingAllowed()).isFalse();
        assertThat(jdbc.queryForObject("select amount_cents from logistics_quote where quote_id=?",Long.class,ours.get(0).quoteId())).isEqualTo(1000);
    }
    @Test void expiresAtExactTenMinutesAndUsesNumericalConditionEquality() {
        Company c=setup("EXPIRY","12"); Quote q=quote(c);
        assertThat(q.expiresAt()).isEqualTo(issued.plusSeconds(600));
        assertThat(service(issued.plusSeconds(599)).getValid(q.quoteId(),77,input("18.5")).quoteId()).isEqualTo(q.quoteId());
        assertThatThrownBy(()->service(issued.plusSeconds(600)).getValid(q.quoteId(),77,input("18.5000"))).isInstanceOf(BusinessException.class).hasMessageContaining("10分钟");
    }
    @Test void ownershipAndChangedConditionsCannotReuseQuotes() {
        Company c=setup("OWNER","12"); Quote q=quote(c);
        assertThatThrownBy(()->service(issued).getValid(q.quoteId(),78,input("18.5"))).isInstanceOf(BusinessException.class).hasMessageContaining("员工");
        assertThatThrownBy(()->service(issued).getValid(q.quoteId(),77,input("18.6"))).isInstanceOf(BusinessException.class).hasMessageContaining("条件");
        var input=input("18.5"); var changed=new QuoteInput(input.senderProvince(),input.senderCity(),input.senderCounty(),input.recipientProvince(),input.recipientCity(),"鼓楼区",input.weight(),input.volume(),input.pieceAmount(),1,1);
        assertThatThrownBy(()->service(issued).getForOrder(q.quoteId(),changed)).isInstanceOf(BusinessException.class).hasMessageContaining("条件");
        assertThat(service(issued).getForOrder(q.quoteId(),input).available()).isTrue();
    }
    @Test void companyVersionChangeOrStoppingInvalidatesIssuedQuote() {
        Company c=setup("STOP","12"); Quote q=quote(c);
        catalog.companyStatus(c.id(),new StatusInput("disabled",c.version()));
        assertThatThrownBy(()->service(issued).getValid(q.quoteId(),77,input("18.5"))).isInstanceOf(BusinessException.class).hasMessageContaining("公司");
    }
    @Test void republishingTemplateInvalidatesIssuedQuoteAndKeepsOldSnapshot() {
        Company c=setup("REPUBLISH","12"); Quote q=quote(c); Template t=catalog.templates(c.id()).getFirst();
        catalog.updateTemplate(t.id(),new TemplateInput(t.name(),t.productCode(),t.productName(),t.originProvince(),t.originCity(),t.originCounty(),rules("30"),"enabled","",t.version()));
        assertThatThrownBy(()->service(issued).getValid(q.quoteId(),77,input("18.5"))).isInstanceOf(BusinessException.class).hasMessageContaining("模板");
        assertThat(jdbc.queryForObject("select amount_cents from logistics_quote where quote_id=?",Long.class,q.quoteId())).isEqualTo(1200);
    }
    @Test void accountReplacementInvalidatesQuoteAndRequiresTemplateRebinding() {
        Company c=catalog.createCompany(new CompanyInput("BIND","绑定","ANE","enabled","","",0,"",null));
        var sender=new Sender("仓库","13800138000","浙江省","杭州市","余杭区","仓库路1号");
        catalog.saveAccount(c.id(),new AccountInput(0,"CODE","C1","D","APP","PASS",catalog.expectedOrderUrl(),sender));
        catalog.createTemplate(c.id(),new TemplateInput("价格","P","产品","浙江省","杭州市","余杭区",rules("12"),"enabled","",0));
        Quote q=quote(c);
        catalog.saveAccount(c.id(),new AccountInput(1,"CODE","C2","D","APP2","PASS2",catalog.expectedOrderUrl(),sender));
        assertThatThrownBy(()->service(issued).getValid(q.quoteId(),77,input("18.5"))).isInstanceOf(BusinessException.class);
        Quote unavailable=quote(c); assertThat(unavailable.available()).isFalse(); assertThat(unavailable.reason()).contains("账号已变更");
    }
    @Test void eachProductExposesOriginMismatchWithoutContaminatingOtherCompanies() {
        Company a=setup("GOOD","12"), b=setup("WRONG_ORIGIN","5"); Template t=catalog.templates(b.id()).getFirst();
        catalog.updateTemplate(t.id(),new TemplateInput(t.name(),t.productCode(),t.productName(),"330000","宁波市","海曙区",t.rules(),"enabled","",0));
        var batch=service(issued).quote(input("18.5"),77);
        assertThat(batch.quotes().stream().filter(q->q.companyId()==a.id()).findFirst().orElseThrow().available()).isTrue();
        assertThat(batch.quotes().stream().filter(q->q.companyId()==b.id()).findFirst().orElseThrow().reason()).contains("始发");
    }
    @Test void corruptAccountConfigurationOnlyMakesThatCompanyUnavailable() {
        Company a=setup("HEALTHY","12");
        Company b=catalog.createCompany(new CompanyInput("CORRUPT","损坏配置","ANE","enabled","","",0,"",null));
        AccountView account=catalog.saveAccount(b.id(),new AccountInput(0,"CODE","C","D","APP","PASS",catalog.expectedOrderUrl(),new Sender("仓库","13800138000","浙江省","杭州市","余杭区","地址")));
        catalog.createTemplate(b.id(),new TemplateInput("价格","P","产品","浙江省","杭州市","余杭区",rules("5"),"enabled","",0));
        jdbc.update("update logistics_account_version set config_json=json_array() where id=?",account.accountVersionId());
        var batch=service(issued).quote(input("18.5"),77);
        assertThat(batch.quotes().stream().filter(q->q.companyId()==a.id()).findFirst().orElseThrow().available()).isTrue();
        Quote bad=batch.quotes().stream().filter(q->q.companyId()==b.id()).findFirst().orElseThrow();
        assertThat(bad.available()).isFalse(); assertThat(bad.reason()).contains("接口配置");
    }    @Test @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void validationSeesPublishedTemplateEvenWhenItsTransactionAlreadyHasAnOldSnapshot() {
        var tx=new org.springframework.transaction.support.TransactionTemplate(transactions);
        record Fixture(Company company,Template template,Quote quote) {}
        Fixture fixture=tx.execute(s->{ Company c=setup("REPEATABLE_READ","12"); return new Fixture(c,catalog.templates(c.id()).getFirst(),quote(c)); });
        try {
            tx.execute(s->{
                jdbc.queryForObject("select version_no from logistics_template where id=?",Long.class,fixture.template().id());
                java.util.concurrent.CompletableFuture.runAsync(()->tx.execute(other->{
                    Template t=fixture.template();
                    catalog.updateTemplate(t.id(),new TemplateInput(t.name(),t.productCode(),t.productName(),t.originProvince(),t.originCity(),t.originCounty(),rules("99"),"enabled","",t.version()));
                    return null;
                })).join();
                assertThatThrownBy(()->service(issued).getValid(fixture.quote().quoteId(),77,input("18.5")))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("模板");
                return null;
            });
        } finally {
            tx.execute(s->{
                jdbc.update("delete from logistics_quote where company_id=?",fixture.company().id());
                jdbc.update("delete from logistics_template_version where template_id=?",fixture.template().id());
                jdbc.update("delete from logistics_template where id=?",fixture.template().id());
                jdbc.update("delete from logistics_company where id=?",fixture.company().id());
                return null;
            });
        }
    }    @Test void corruptTemplateConfigurationOnlyMakesThatCompanyUnavailable() {
        Company a=setup("HEALTHY_TEMPLATE","12"),b=setup("CORRUPT_TEMPLATE","5");
        jdbc.update("update logistics_template set config_json=json_array() where company_id=?",b.id());
        var batch=service(issued).quote(input("18.5"),77);
        assertThat(batch.quotes().stream().filter(q->q.companyId()==a.id()).findFirst().orElseThrow().available()).isTrue();
        Quote bad=batch.quotes().stream().filter(q->q.companyId()==b.id()).findFirst().orElseThrow();
        assertThat(bad.available()).isFalse(); assertThat(bad.reason()).contains("模板配置");
    }}
