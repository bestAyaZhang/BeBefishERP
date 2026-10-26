package com.bebefish.erp.logistics;

import static com.bebefish.erp.logistics.LogisticsDtos.*;
import static com.bebefish.erp.logistics.LogisticsCatalogService.*;
import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.logistics.pricing.FreightCalculator;
import com.bebefish.erp.logistics.pricing.FreightRules;
import com.bebefish.erp.logistics.pricing.ProvinceCatalog;
import com.bebefish.erp.shipping.domain.ShipmentFormInput;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FreightQuoteService {
    private final JdbcTemplate jdbc;
    private final LogisticsCatalogService catalog;
    private final Clock clock;
    private final FreightCalculator calculator=new FreightCalculator();
    public FreightQuoteService(JdbcTemplate jdbc,LogisticsCatalogService catalog,Clock clock) {
        this.jdbc=jdbc; this.catalog=catalog; this.clock=clock;
    }
    public boolean managedTemplatesEnabled() {
        return jdbc.queryForObject("select count(*) from logistics_template where environment=? and status='enabled'",Long.class,catalog.runtimeEnvironment())>0;
    }
    public static QuoteInput input(ShipmentFormInput form) {
        if(form==null) throw invalid("缺少发货资料"); var d=form.orderDraft();
        return canonical(new QuoteInput(form.senderProvince(),form.senderCity(),form.senderCounty(),form.recipientProvince(),form.recipientCity(),form.recipientCounty(),
            d==null?null:d.weight(),d==null?null:d.volume(),d==null||d.pieceAmount()==null?null:BigDecimal.valueOf(d.pieceAmount()),d==null?null:d.goodsType(),d==null?null:d.payType()));
    }
    /** Used for persisted conditions as well as validation; decimal scale is never a price condition. */
    public static QuoteInput canonical(QuoteInput in) {
        if(in==null) throw invalid("缺少报价条件");
        return new QuoteInput(province(in.senderProvince()),clean(in.senderCity()),clean(in.senderCounty()),province(in.recipientProvince()),clean(in.recipientCity()),clean(in.recipientCounty()),
            decimal(in.weight()),decimal(in.volume()),decimal(in.pieceAmount()),in.goodsType(),in.payType());
    }
    private static String province(String in) { String normalized=ProvinceCatalog.normalize(in); return normalized==null?clean(in):normalized; }
    private static BigDecimal decimal(BigDecimal in) { return in==null?null:in.stripTrailingZeros(); }
    @Transactional public QuoteBatch quote(QuoteInput input,long employeeId) {
        if(employeeId<=0) throw invalid("报价需要有效员工身份"); QuoteInput conditions=canonical(input);
        Instant now=clock.instant().truncatedTo(ChronoUnit.MILLIS),expires=now.plus(10,ChronoUnit.MINUTES);
        List<Quote> results=new ArrayList<>(); Map<Long,Long> companyVersions=new HashMap<>();
        for(Company company:catalog.enabledCompanies()) {
            companyVersions.put(company.id(),company.version());
            List<Template> templates;
            try { templates=catalog.templates(company.id()).stream().filter(t->"enabled".equals(t.status())).toList(); }
            catch(BusinessException|IllegalStateException e) { results.add(unavailable(company,null,null,"运费模板配置无效，请联系管理员检查")); continue; }
            if(templates.isEmpty()) { results.add(unavailable(company,null,null,"未配置已启用运费模板")); continue; }
            AccountView account;
            try { account=catalog.account(company.id()); }
            catch(BusinessException|IllegalStateException e) {
                for(Template template:templates) results.add(unavailable(company,null,template,"接口配置无效，请联系管理员检查"));
                continue;
            }
            for(Template template:templates) {
                try { results.add(calculate(company,account,template,conditions,expires)); }
                catch(BusinessException|IllegalArgumentException|IllegalStateException e) { results.add(unavailable(company,account,template,"模板或接口配置无效，请联系管理员检查")); }
            }
        }
        int recommended=-1;
        for(int i=0;i<results.size();i++) if(results.get(i).available() && (recommended<0||results.get(i).amount().compareTo(results.get(recommended).amount())<0)) recommended=i;
        if(recommended>=0) results.set(recommended,results.get(recommended).recommended(true));
        for(Quote result:results) if(result.available()) {
            long cents;
            try { cents=result.amount().movePointRight(2).longValueExact(); }
            catch(ArithmeticException e) { throw invalid("报价金额超过可保存范围"); }
            jdbc.update("insert into logistics_quote(quote_id,employee_id,environment,company_id,company_version,account_id,account_version_id,template_id,template_version,conditions_json,result_json,amount_cents,issued_at,expires_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                result.quoteId(),employeeId,catalog.runtimeEnvironment(),result.companyId(),companyVersions.get(result.companyId()),result.accountId(),result.accountVersionId(),result.templateId(),result.templateVersion(),catalog.json(conditions),catalog.json(result),cents,ts(now),ts(expires));
        }
        return new QuoteBatch(results,!managedTemplatesEnabled(),now);
    }
    private Quote calculate(Company company,AccountView account,Template t,QuoteInput in,Instant expires) {
        if(!Objects.equals(t.accountId(),account==null?null:account.id())) return unavailable(company,account,t,"模板绑定账号已变更，请重新发布模板");
        if(!Objects.equals(t.originProvince(),in.senderProvince())||!Objects.equals(t.originCity(),in.senderCity())||!Objects.equals(t.originCounty(),in.senderCounty()))
            return unavailable(company,account,t,"始发省市区与模板不匹配");
        calculator.validateRules(t.rules(),true);
        var result=calculator.quote(t.rules(),new FreightRules.Input(in.recipientProvince(),in.weight(),in.volume(),in.pieceAmount()));
        if(!result.available()) return unavailable(company,account,t,result.reason());
        var b=result.band(); boolean ready=account!=null&&account.ready()&&company.provider().equals("ANE");
        return new Quote(UUID.randomUUID().toString(),company.id(),company.name(),company.provider(),account==null?null:account.id(),account==null?null:account.accountVersionId(),
            t.id(),t.version(),t.productCode(),t.productName(),result.amount(),result.billedQuantity(),t.rules().mode(),result.matchedRegion(),b.lowerExclusive(),b.upperInclusive(),b.firstQuantity(),b.firstFee(),b.additionalQuantity(),b.additionalFee(),result.renewalCount(),true,"",ready,apiMessage(company,account),false,expires);
    }
    private Quote unavailable(Company c,AccountView account,Template t,String reason) {
        return new Quote(null,c.id(),c.name(),c.provider(),account==null?null:account.id(),account==null?null:account.accountVersionId(),t==null?null:t.id(),t==null?null:t.version(),t==null?"":t.productCode(),t==null?"":t.productName(),
            null,null,t==null||t.rules()==null?"":t.rules().mode(),"",null,null,null,null,null,null,null,false,reason,account!=null&&account.ready()&&c.provider().equals("ANE"),apiMessage(c,account),false,null);
    }
    private String apiMessage(Company c,AccountView account) { return c.provider().equals("MANUAL")?"该公司仅支持线下发货":account==null?"未配置接口账号":account.message(); }
    @Transactional public Quote getValid(String quoteId,long employeeId,QuoteInput input) { return validate(quoteId,employeeId,input); }
    @Transactional public Quote getForOrder(String quoteId,QuoteInput input) { return validate(quoteId,null,input); }
    private Quote validate(String quoteId,Long owner,QuoteInput input) {
        try { if(quoteId==null||!UUID.fromString(quoteId).toString().equalsIgnoreCase(quoteId)) throw new IllegalArgumentException(); }
        catch(IllegalArgumentException e) { throw invalid("报价编号无效，请重新报价"); }
        return jdbc.query("select * from logistics_quote where quote_id=?",(r,n)->{
            if(!catalog.runtimeEnvironment().equals(r.getString("environment"))) throw stale("报价不属于当前运行环境");
            if(owner!=null && owner!=r.getLong("employee_id")) throw stale("报价不属于当前员工");
            if(!clock.instant().isBefore(r.getTimestamp("expires_at").toInstant())) throw stale("报价已超过10分钟，请重新报价");
            QuoteInput stored=canonical(catalog.read(r.getString("conditions_json"),QuoteInput.class));
            if(!stored.equals(canonical(input))) throw stale("发货条件已变更，请重新报价");
            long companyId=r.getLong("company_id");
            // Management writes lock the same company row. A surrounding shipment transaction keeps this lock until its save completes.
            Company company=catalog.lockCompany(companyId);
            if(!company.status().equals("enabled")||company.version()!=r.getLong("company_version")) throw stale("物流公司资料已变更，请重新报价");
            Template template=catalog.lockTemplate(r.getLong("template_id"));
            if(!template.status().equals("enabled")||template.version()!=r.getLong("template_version")) throw stale("运费模板已变更，请重新报价");
            AccountView current=catalog.account(companyId);
            Long accountId=r.getObject("account_id",Long.class),versionId=r.getObject("account_version_id",Long.class);
            if(!Objects.equals(accountId,current==null?null:current.id())||!Objects.equals(versionId,current==null?null:current.accountVersionId())||!Objects.equals(template.accountId(),accountId))
                throw stale("物流账号已变更，请重新报价");
            if(!clock.instant().isBefore(r.getTimestamp("expires_at").toInstant())) throw stale("报价已超过10分钟，请重新报价");
            return catalog.read(r.getString("result_json"),Quote.class);
        },quoteId).stream().findFirst().orElseThrow(()->stale("报价不存在，请重新报价"));
    }
    private static BusinessException stale(String message) { return conflict("FREIGHT_QUOTE_STALE",message); }
}
