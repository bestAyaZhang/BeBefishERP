package com.bebefish.erp.logistics;

import static org.assertj.core.api.Assertions.*;
import static com.bebefish.erp.logistics.LogisticsDtos.*;
import static com.bebefish.erp.logistics.LogisticsPersistenceTest.input;
import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.logistics.pricing.FreightRules.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Base64;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties="ERP_LOGISTICS_MASTER_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=") @ActiveProfiles("test") @Transactional
class LogisticsReviewRegressionTest {
    @Autowired LogisticsCatalogService catalog; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper mapper; @Autowired PlatformTransactionManager transactions;
    final Instant issued=Instant.parse("2026-10-26T04:00:00Z");
    Rules rules() { return new Rules("weight",false,null,List.of(new Band(BigDecimal.ZERO,null,new BigDecimal("20"),new BigDecimal("12"),BigDecimal.ONE,BigDecimal.ONE)),List.of()); }
    Company company(String code,String provider) { return catalog.createCompany(new CompanyInput(code,code,provider,"enabled","","",0,"",null)); }
    AccountInput account(long version,String customer) { return new AccountInput(version,"CODE",customer,"D","APP-SECRET","PASS-SECRET",catalog.expectedOrderUrl(),new Sender("仓库","13800138000","浙江省","杭州市","余杭区","地址")); }
    TemplateInput template(String status,long version) { return new TemplateInput("价格","P","标准快运","浙江省","杭州市","余杭区",rules(),status,"",version); }
    void cleanup(long companyId) {
        jdbc.update("delete from logistics_quote where company_id=?",companyId);
        jdbc.update("delete from logistics_template_version where template_id in(select id from logistics_template where company_id=?)",companyId);
        jdbc.update("delete from logistics_template where company_id=?",companyId);
        jdbc.update("delete from logistics_account_version where account_id in(select id from logistics_account where company_id=?)",companyId);
        jdbc.update("delete from logistics_account where company_id=?",companyId);
        jdbc.update("delete from logistics_company where id=?",companyId);
    }
    @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void publicationBindsTheCurrentAccountAfterAnOlderSnapshotWasEstablished() {
        var tx=new TransactionTemplate(transactions);
        record Fixture(Company company,Template template) {}
        Fixture f=tx.execute(s->{ Company c=company("PUBLISH_CURRENT","ANE"); catalog.saveAccount(c.id(),account(0,"C1")); return new Fixture(c,catalog.createTemplate(c.id(),template("disabled",0))); });
        try {
            tx.execute(s->{
                catalog.template(f.template().id());
                AccountView current=CompletableFuture.supplyAsync(()->tx.execute(other->catalog.saveAccount(f.company().id(),account(1,"C2")))).join();
                Template published=catalog.templateStatus(f.template().id(),new StatusInput("enabled",0L));
                assertThat(published.accountId()).isEqualTo(current.id());
                return null;
            });
        } finally { tx.execute(s->{ cleanup(f.company().id()); return null; }); }
    }
    @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void publicationReportsAConflictAgainstTheCurrentTemplateVersion() {
        var tx=new TransactionTemplate(transactions);
        Template f=tx.execute(s->{ Company c=company("PUBLISH_VERSION","MANUAL"); return catalog.createTemplate(c.id(),template("enabled",0)); });
        try {
            tx.execute(s->{
                catalog.template(f.id());
                CompletableFuture.runAsync(()->tx.execute(other->catalog.updateTemplate(f.id(),template("enabled",0)))).join();
                assertThatThrownBy(()->catalog.updateTemplate(f.id(),template("enabled",0)))
                    .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.code()).isEqualTo("LOGISTICS_VERSION_CONFLICT"));
                s.setRollbackOnly();
                return null;
            });
        } finally { tx.execute(s->{ cleanup(f.companyId()); return null; }); }
    }
    @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void expiryWhileWaitingForCompanyLockRejectsBothValidationEntryPoints() throws Exception {
        var tx=new TransactionTemplate(transactions);
        Company c=tx.execute(s->{ Company created=company("WAIT_EXPIRY","MANUAL"); catalog.createTemplate(created.id(),template("enabled",0)); return created; });
        Quote q=tx.execute(s->new FreightQuoteService(jdbc,catalog,Clock.fixed(issued,ZoneOffset.UTC)).quote(input("18.5"),77).quotes().stream().filter(x->x.companyId()==c.id()).findFirst().orElseThrow());
        try {
            for(boolean ordering:List.of(false,true)) {
                CountDownLatch locked=new CountDownLatch(1),release=new CountDownLatch(1),clockRead=new CountDownLatch(1);
                AtomicReference<Instant> now=new AtomicReference<>(issued.plusSeconds(599));
                Clock clock=new Clock() { public ZoneId getZone(){return ZoneOffset.UTC;} public Clock withZone(ZoneId z){return this;} public Instant instant(){ Instant captured=now.get(); clockRead.countDown(); return captured;} };
                var holder=CompletableFuture.runAsync(()->tx.execute(s->{ jdbc.queryForObject("select id from logistics_company where id=? for update",Long.class,c.id()); locked.countDown(); await(release); return null; }));
                assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();
                var validator=CompletableFuture.supplyAsync(()->{
                    try { return tx.execute(s->{ var service=new FreightQuoteService(jdbc,catalog,clock); return ordering?service.getForOrder(q.quoteId(),input("18.5")):service.getValid(q.quoteId(),77,input("18.5")); }); }
                    catch(BusinessException error) { return error; }
                });
                try { assertThat(clockRead.await(5,TimeUnit.SECONDS)).isTrue(); now.set(issued.plusSeconds(600)); }
                finally { release.countDown(); }
                holder.get(5,TimeUnit.SECONDS);
                assertThat(validator.get(5,TimeUnit.SECONDS)).isInstanceOf(BusinessException.class);
            }
        } finally { tx.execute(s->{ cleanup(c.id()); return null; }); }
    }
    @Test void wrongValidMasterKeyDisablesApiReadinessButPreservesOfflinePrice() {
        Company c=company("WRONG_KEY","ANE"); catalog.saveAccount(c.id(),account(0,"C1")); catalog.createTemplate(c.id(),template("enabled",0));
        byte[] changed=new byte[32]; changed[0]=1;
        var wrong=new LogisticsCatalogService(jdbc,mapper,new LogisticsSecretCipher(Base64.getEncoder().encodeToString(changed)),Clock.systemUTC(),new MockEnvironment());
        assertThat(wrong.account(c.id()).ready()).isFalse();
        assertThat(wrong.account(c.id()).message()).contains("验证");
        Quote quote=new FreightQuoteService(jdbc,wrong,Clock.fixed(issued,ZoneOffset.UTC)).quote(input("18.5"),77).quotes().stream().filter(q->q.companyId()==c.id()).findFirst().orElseThrow();
        assertThat(quote.available()).isTrue(); assertThat(quote.amount()).isEqualByComparingTo("12"); assertThat(quote.apiReady()).isFalse();
    }
    @Test void tamperedSavedCiphertextDisablesApiReadinessButPreservesOfflinePrice() {
        Company c=company("TAMPERED_READY","ANE"); AccountView account=catalog.saveAccount(c.id(),account(0,"C1")); catalog.createTemplate(c.id(),template("enabled",0));
        jdbc.update("update logistics_account_version set secret_cipher='AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA' where id=?",account.accountVersionId());
        assertThat(catalog.account(c.id()).ready()).isFalse();
        Quote quote=new FreightQuoteService(jdbc,catalog,Clock.fixed(issued,ZoneOffset.UTC)).quote(input("18.5"),77).quotes().stream().filter(q->q.companyId()==c.id()).findFirst().orElseThrow();
        assertThat(quote.available()).isTrue(); assertThat(quote.apiReady()).isFalse();
    }
    static void await(CountDownLatch latch) { try { if(!latch.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("transaction synchronization timed out"); } catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); } }
}
