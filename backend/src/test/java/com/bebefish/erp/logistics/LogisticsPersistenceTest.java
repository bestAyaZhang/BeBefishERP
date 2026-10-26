package com.bebefish.erp.logistics;

import static org.assertj.core.api.Assertions.*;
import static com.bebefish.erp.logistics.LogisticsDtos.*;
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
class LogisticsPersistenceTest {
    @Autowired LogisticsCatalogService catalog; @Autowired CarrierAccountResolver resolver; @Autowired JdbcTemplate jdbc; @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;
    Company company(String code,String provider) { return catalog.createCompany(new CompanyInput(code,code,provider,"enabled","","",0,"",null)); }
    AccountInput account(long version,String customer,String app,String pass) {
        return new AccountInput(version,"PORTAL",customer,"DICT",app,pass,catalog.expectedOrderUrl(),new Sender("仓库","13800138000","浙江省","杭州市","余杭区","仓库路1号"));
    }
    Rules rules(String fee) { return new Rules("weight",false,new BigDecimal("6000"),List.of(new Band(BigDecimal.ZERO,null,BigDecimal.ONE,new BigDecimal(fee),BigDecimal.ONE,new BigDecimal("2"))),List.of()); }
    TemplateInput template(String code,Rules rules,String status,long version) { return new TemplateInput("模板",code,"标准快运","浙江省","杭州市","余杭区",rules,status,"",version); }
    @Test void immutableEncryptedVersionsPreserveOnlySameIdentitySecretsAndResolveHistoricalSnapshots() {
        Company c=company("ACCOUNT","ANE");
        AccountView first=catalog.saveAccount(c.id(),account(0,"CUST1","APP-ONE","PASS-ONE"));
        assertThat(first.ready()).isTrue(); assertThat(first.appKeyConfigured()).isTrue();
        String publicJson=jdbc.queryForObject("select config_json from logistics_account_version where id=?",String.class,first.accountVersionId());
        String ciphertext=jdbc.queryForObject("select secret_cipher from logistics_account_version where id=?",String.class,first.accountVersionId());
        assertThat(publicJson).doesNotContain("APP-ONE","PASS-ONE"); assertThat(ciphertext).doesNotContain("APP-ONE","PASS-ONE");
        AccountView second=catalog.saveAccount(c.id(),account(1,"CUST1","",""));
        assertThat(second.id()).isEqualTo(first.id()); assertThat(second.accountVersionId()).isNotEqualTo(first.accountVersionId());
        assertThat(resolver.resolve(second.accountVersionId()).config().appKey()).isEqualTo("APP-ONE");
        assertThat(resolver.resolve(first.accountVersionId()).config().customerPass()).isEqualTo("PASS-ONE");
        AccountView replacement=catalog.saveAccount(c.id(),account(2,"CUST2","",""));
        assertThat(replacement.id()).isNotEqualTo(first.id()); assertThat(replacement.appKeyConfigured()).isFalse(); assertThat(replacement.ready()).isFalse();
        assertThat(catalog.accounts(c.id())).hasSize(2);
        assertThat(resolver.resolve(first.accountVersionId()).config().customerCode()).isEqualTo("CUST1");
        assertThatThrownBy(()->catalog.saveAccount(c.id(),account(2,"CUST2","x","y"))).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("select count(*) from logistics_account_version where account_id=?",Long.class,first.id())).isEqualTo(2);
    }
    @Test void rejectsWrongEnvironmentHostPortQueryAndManualNetworkCapabilities() {
        Company c=company("HOSTS","ANE");
        for(String url:List.of("https://opc.ane56.com/aneop/opwb/lb/new","https://evil.ane56.com/aneop/opwb/lb/new","http://opc.test.ane56.com/aneop/opwb/lb/new","https://opc.test.ane56.com:443/aneop/opwb/lb/new","https://opc.test.ane56.com/aneop/opwb/lb/new?x=1")) {
            AccountInput a=account(0,"CUST","APP","PASS");
            assertThatThrownBy(()->catalog.saveAccount(c.id(),new AccountInput(0,a.code(),a.customerCode(),a.dictId(),a.appKey(),a.customerPass(),url,a.sender())))
                .isInstanceOf(BusinessException.class).hasMessageContaining("固定HTTPS");
        }
        Company manual=company("OFFLINE","MANUAL");
        assertThatThrownBy(()->catalog.saveAccount(manual.id(),account(0,"CUST","APP","PASS"))).isInstanceOf(BusinessException.class);
        AccountView stored=catalog.saveAccount(manual.id(),account(0,"CUST","",""));
        assertThat(stored.orderUrl()).isEmpty(); assertThat(stored.ready()).isFalse();
    }
    @Test void snapshotsAreAtomicAndMasterKeyAbsenceAllowsReadsAndManualConfiguration() {
        Company c=company("SNAPSHOT","ANE");
        AccountView first=catalog.saveAccount(c.id(),account(0,"CUST","APP-ONE","PASS-ONE"));
        AccountInput next=new AccountInput(1,"PORTAL","CUST","DICT","APP-TWO","PASS-TWO",catalog.expectedOrderUrl(),new Sender("新仓库","13900139000","浙江省","宁波市","海曙区","新地址"));
        AccountView second=catalog.saveAccount(c.id(),next);
        var old=resolver.resolve(first.accountVersionId()).config(); var current=resolver.resolve(second.accountVersionId()).config();
        assertThat(old.sender().name()).isEqualTo("仓库"); assertThat(old.appKey()).isEqualTo("APP-ONE");
        assertThat(current.sender().name()).isEqualTo("新仓库"); assertThat(current.customerPass()).isEqualTo("PASS-TWO");
        assertThat(first.toString()).doesNotContain("APP-ONE","PASS-ONE"); assertThat(next.toString()).doesNotContain("APP-TWO","PASS-TWO");
    }
    @Test void templateVersionSnapshotsAndEnabledUniquenessAreEnforced() {
        Company c=company("TEMPLATES","MANUAL"); Template first=catalog.createTemplate(c.id(),template("P",rules("10"),"enabled",0));
        assertThat(first.originProvince()).isEqualTo("330000");
        Template updated=catalog.updateTemplate(first.id(),template("P",rules("20"),"enabled",0));
        assertThat(updated.version()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from logistics_template_version where template_id=?",Long.class,first.id())).isEqualTo(2);
        assertThat(jdbc.queryForObject("select config_json from logistics_template_version where template_id=? and version_no=0",String.class,first.id())).contains("10");
        assertThatThrownBy(()->catalog.updateTemplate(first.id(),template("P",rules("30"),"enabled",0))).isInstanceOf(BusinessException.class);
    }
    @Test void duplicateActiveTemplateIsRejectedButDisabledIncompleteDraftIsAllowed() {
        Company c=company("UNIQUE_TEMPLATE","MANUAL"); catalog.createTemplate(c.id(),template("P",rules("10"),"enabled",0));
        assertThatThrownBy(()->catalog.createTemplate(c.id(),template("P",rules("20"),"enabled",0))).isInstanceOf(BusinessException.class).hasMessageContaining("只能启用一个");
    }
    @Test void invalidDraftCannotBePublishedAndDoesNotParticipateInQuotes() {
        Company c=company("DRAFT","MANUAL");
        Template draft=catalog.createTemplate(c.id(),template("P",new Rules("weight",false,null,List.of(),List.of()),"disabled",0));
        var svc=new FreightQuoteService(jdbc,catalog,Clock.systemUTC());
        assertThat(svc.quote(input("18.5"),99).quotes().stream().filter(q->q.companyId()==c.id()).toList()).allMatch(q->!q.available()&&q.amount()==null);
        assertThatThrownBy(()->catalog.templateStatus(draft.id(),new StatusInput("enabled",0L))).isInstanceOf(BusinessException.class);
    }
    static QuoteInput input(String weight) { return new QuoteInput("浙江省"," 杭州市 ","余杭区","江苏省","南京市","江宁区",new BigDecimal(weight),new BigDecimal("0.100"),new BigDecimal("1.0"),1,1); }
    @Test void readModelsNeverSerializeSecretsAndAccountsStayWithinRuntimeEnvironment() throws Exception {
        Company c=company("ENVIRONMENT","ANE"); AccountView first=catalog.saveAccount(c.id(),account(0,"CUST","APP-SECRET","PASS-SECRET"));
        assertThat(mapper.writeValueAsString(first)).doesNotContain("APP-SECRET","PASS-SECRET","\"appKey\":","\"customerPass\":");
        var noKey=new LogisticsCatalogService(jdbc,mapper,new LogisticsSecretCipher(""),Clock.systemUTC(),new org.springframework.mock.env.MockEnvironment());
        assertThat(noKey.account(c.id()).appKeyConfigured()).isTrue(); assertThat(noKey.account(c.id()).ready()).isFalse();
        var prodEnv=new org.springframework.mock.env.MockEnvironment(); prodEnv.setActiveProfiles("prod");
        var prod=new LogisticsCatalogService(jdbc,mapper,new LogisticsSecretCipher("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="),Clock.systemUTC(),prodEnv);
        assertThat(prod.expectedOrderUrl()).isEqualTo("https://opc.ane56.com/aneop/opwb/lb/new");
        assertThat(prod.account(c.id())).isNull();
        assertThatThrownBy(()->prod.resolveVersion(first.accountVersionId())).isInstanceOf(BusinessException.class).hasMessageContaining("运行环境");
    }}
