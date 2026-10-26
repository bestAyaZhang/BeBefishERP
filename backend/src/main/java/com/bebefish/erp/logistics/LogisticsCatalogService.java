package com.bebefish.erp.logistics;

import static com.bebefish.erp.logistics.LogisticsDtos.*;
import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.common.api.PageResponse;
import com.bebefish.erp.logistics.CarrierAccountResolver.ResolvedAccount;
import com.bebefish.erp.logistics.pricing.FreightCalculator;
import com.bebefish.erp.logistics.pricing.ProvinceCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogisticsCatalogService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final LogisticsSecretCipher cipher;
    private final Clock clock;
    private final String environment;
    private final FreightCalculator calculator=new FreightCalculator();
    public LogisticsCatalogService(JdbcTemplate jdbc,ObjectMapper mapper,LogisticsSecretCipher cipher,Clock clock,Environment env) {
        this.jdbc=jdbc; this.mapper=mapper; this.cipher=cipher; this.clock=clock;
        this.environment=Arrays.asList(env.getActiveProfiles()).contains("prod")?"production":"test";
    }
    public String runtimeEnvironment() { return environment; }
    private String accountColumn() { return environment.equals("production")?"production_account_id":"test_account_id"; }
    private String revisionColumn() { return environment.equals("production")?"production_account_revision":"test_account_revision"; }
    public PageResponse<Company> companies(int page,int size,String keyword,String status) {
        page(page,size); if(!clean(status).isEmpty()) status(status);
        String pattern="%"+clean(keyword).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        String where=" where (?='' or code like ? escape '!' or name like ? escape '!') and (?='' or status=?)";
        List<Company> records=jdbc.query("select * from logistics_company"+where+" order by sort_order,id limit ? offset ?",this::companyRow,
                clean(keyword),pattern,pattern,clean(status),clean(status),size,(long)(page-1)*size);
        long total=jdbc.queryForObject("select count(*) from logistics_company"+where,Long.class,clean(keyword),pattern,pattern,clean(status),clean(status));
        return new PageResponse<>(records,page,size,total);
    }
    public Company company(long id) {
        return jdbc.query("select * from logistics_company where id=?",this::companyRow,id).stream().findFirst().orElseThrow(()->missing("物流公司不存在"));
    }
    Company lockCompany(long id) {
        return jdbc.query("select * from logistics_company where id=? for update",this::companyRow,id).stream().findFirst().orElseThrow(()->missing("物流公司不存在"));
    }
    @Transactional public Company createCompany(CompanyInput in) {
        var c=validatedCompany(in,null); Instant now=clock.instant();
        try {
            long id=insert("insert into logistics_company(code,name,provider,status,contact_name,contact_phone,sort_order,remark,version_no,created_at,updated_at) values(?,?,?,?,?,?,?,?,0,?,?)",
                    c.code(),c.name(),c.provider(),c.status(),c.contactName(),c.contactPhone(),c.sortOrder(),c.remark(),ts(now),ts(now));
            return company(id);
        } catch(DuplicateKeyException e) { throw duplicate("物流公司编码已存在"); }
    }
    @Transactional public Company updateCompany(long id,CompanyInput in) {
        Company old=lockCompany(id); expected(in.version(),old.version()); CompanyInput c=validatedCompany(in,old);
        if(!old.provider().equals(c.provider()) && (jdbc.queryForObject("select count(*) from logistics_account where company_id=?",Long.class,id)>0
                ||jdbc.queryForObject("select count(*) from logistics_template where company_id=?",Long.class,id)>0))
            throw conflict("LOGISTICS_PROVIDER_LOCKED","已有账号或模板的公司不能更改服务商类型");
        try {
            jdbc.update("update logistics_company set code=?,name=?,provider=?,status=?,contact_name=?,contact_phone=?,sort_order=?,remark=?,version_no=version_no+1,updated_at=? where id=?",
                c.code(),c.name(),c.provider(),c.status(),c.contactName(),c.contactPhone(),c.sortOrder(),c.remark(),ts(clock.instant()),id);
            return company(id);
        } catch(DuplicateKeyException e) { throw duplicate("物流公司编码已存在"); }
    }
    @Transactional public Company companyStatus(long id,StatusInput in) {
        Company old=lockCompany(id); if(in==null) throw invalid("缺少状态资料"); expected(in.version(),old.version()); status(in.status());
        jdbc.update("update logistics_company set status=?,version_no=version_no+1,updated_at=? where id=?",in.status(),ts(clock.instant()),id);
        return company(id);
    }
    private CompanyInput validatedCompany(CompanyInput in,Company old) {
        if(in==null) throw invalid("请填写物流公司资料");
        String code=clean(in.code()).toUpperCase(Locale.ROOT);
        if(code.isEmpty() && old!=null) code=old.code();
        if(!code.matches("[A-Z0-9][A-Z0-9_-]{0,49}")) throw invalid("物流编码只能使用字母、数字、下划线或短横线，最多50位");
        String name=text(in.name(),100,"物流公司名称",true);
        String provider=clean(in.provider()).isEmpty()?(old==null?"MANUAL":old.provider()):clean(in.provider());
        if(!Set.of("ANE","MANUAL").contains(provider)) throw invalid("物流服务商类型无效");
        String status=clean(in.status()).isEmpty()?(old==null?"enabled":old.status()):clean(in.status()); status(status);
        int sort=in.sortOrder()==null?0:in.sortOrder(); if(sort<0||sort>9999) throw invalid("排序值必须为0至9999");
        return new CompanyInput(code,name,provider,status,text(in.contactName(),100,"联系人",false),text(in.contactPhone(),50,"联系电话",false),sort,text(in.remark(),1000,"备注",false),in.version());
    }
    public AccountView account(long companyId) {
        company(companyId); Long id=currentAccountId(companyId); return id==null?null:viewCurrent(id);
    }
    public List<AccountView> accounts(long companyId) {
        company(companyId);
        return jdbc.query("select id from logistics_account where company_id=? and environment=? order by id desc",(r,n)->r.getLong(1),companyId,environment).stream().map(this::viewCurrent).toList();
    }
    private AccountView viewCurrent(long accountId) {
        return jdbc.query("select v.*,a.company_id,a.environment,c.provider from logistics_account a join logistics_account_version v on v.id=a.current_version_id join logistics_company c on c.id=a.company_id where a.id=?",
            (r,n)->accountView(r),accountId).stream().findFirst().orElseThrow(()->missing("物流账号不存在"));
    }
    private AccountView accountView(ResultSet r) throws SQLException {
        AccountInput c=read(r.getString("config_json"),AccountInput.class);
        boolean app=r.getBoolean("app_key_configured"),pass=r.getBoolean("customer_pass_configured");
        boolean ready=ready(r.getString("provider"),c,app,pass) && cipher.available();
        String message=ready?"接口资料已就绪":r.getString("provider").equals("MANUAL")?"该公司仅支持线下发货":!cipher.available()?"未配置物流加密主密钥":"接口资料尚未填写完整";
        return new AccountView(r.getLong("account_id"),r.getLong("company_id"),r.getString("environment"),r.getLong("version_no"),r.getLong("id"),
                c.code(),c.customerCode(),c.dictId(),c.orderUrl(),c.sender(),app,pass,ready,message);
    }
    @Transactional public AccountView saveAccount(long companyId,AccountInput in) {
        Company company=lockCompany(companyId); if(in==null) throw invalid("请填写账号资料");
        long revision=jdbc.queryForObject("select "+revisionColumn()+" from logistics_company where id=?",Long.class,companyId);
        expected(in.version(),revision);
        AccountInput normalized=normalizeAccount(in,company.provider());
        String hash=hash(normalized.code()+"\u0000"+normalized.customerCode()+"\u0000"+normalized.dictId());
        Long oldId=currentAccountId(companyId); Long oldVersion=null; String oldHash=null;
        if(oldId!=null) {
            oldVersion=jdbc.queryForObject("select current_version_id from logistics_account where id=?",Long.class,oldId);
            oldHash=jdbc.queryForObject("select identity_hash from logistics_account where id=?",String.class,oldId);
        }
        String app=clean(normalized.appKey()),pass=clean(normalized.customerPass());
        if(oldId!=null && hash.equals(oldHash) && (app.isEmpty()||pass.isEmpty())) {
            AccountInput previous=resolveVersion(oldVersion).config();
            if(app.isEmpty()) app=previous.appKey(); if(pass.isEmpty()) pass=previous.customerPass();
        }
        if(company.provider().equals("MANUAL") && (!app.isEmpty()||!pass.isEmpty())) throw invalid("线下物流公司不能配置接口密钥");
        if((!app.isEmpty()||!pass.isEmpty()) && !cipher.available()) throw conflict("LOGISTICS_MASTER_KEY_MISSING","未配置物流加密主密钥，无法保存接口密钥");
        long next=revision+1; Instant now=clock.instant();
        List<Long> identities=jdbc.query("select id from logistics_account where company_id=? and environment=? and identity_hash=?",(r,n)->r.getLong(1),companyId,environment,hash);
        long accountId=identities.isEmpty()?insert("insert into logistics_account(company_id,environment,identity_hash,created_at) values(?,?,?,?)",companyId,environment,hash,ts(now)):identities.getFirst();
        AccountInput complete=new AccountInput(next,normalized.code(),normalized.customerCode(),normalized.dictId(),app,pass,normalized.orderUrl(),normalized.sender());
        AccountInput publicConfig=publicConfig(complete);
        String secret=app.isEmpty()&&pass.isEmpty()?null:cipher.encrypt(json(complete),context(accountId,environment,next));
        long versionId=insert("insert into logistics_account_version(account_id,version_no,config_json,secret_cipher,app_key_configured,customer_pass_configured,created_at) values(?,?,?,?,?,?,?)",
            accountId,next,json(publicConfig),secret,!app.isEmpty(),!pass.isEmpty(),ts(now));
        jdbc.update("update logistics_account set current_version_id=? where id=?",versionId,accountId);
        jdbc.update("update logistics_company set "+accountColumn()+"=?,"+revisionColumn()+"=?,version_no=version_no+1,updated_at=? where id=?",accountId,next,ts(now),companyId);
        return viewCurrent(accountId);
    }
    public Optional<ResolvedAccount> currentResolved(long companyId) {
        company(companyId); Long id=currentAccountId(companyId); if(id==null) return Optional.empty();
        Long version=jdbc.queryForObject("select current_version_id from logistics_account where id=?",Long.class,id);
        return Optional.of(resolveVersion(version));
    }
    public ResolvedAccount resolveVersion(long versionId) {
        return jdbc.query("select v.*,a.company_id,a.environment,c.provider from logistics_account_version v join logistics_account a on a.id=v.account_id join logistics_company c on c.id=a.company_id where v.id=?",(r,n)->{
            String env=r.getString("environment"); if(!environment.equals(env)) throw conflict("LOGISTICS_ENVIRONMENT_MISMATCH","物流账号不属于当前运行环境");
            long accountId=r.getLong("account_id"),revision=r.getLong("version_no");
            String encrypted=r.getString("secret_cipher");
            AccountInput config=read(encrypted==null?r.getString("config_json"):cipher.decrypt(encrypted,context(accountId,env,revision)),AccountInput.class);
            if("ANE".equals(r.getString("provider"))) validateOrderUrl(config.orderUrl());
            return new ResolvedAccount(r.getLong("company_id"),accountId,versionId,r.getString("provider"),env,config);
        },versionId).stream().findFirst().orElseThrow(()->missing("物流账号版本不存在"));
    }
    Long currentAccountId(long companyId) { return jdbc.queryForObject("select "+accountColumn()+" from logistics_company where id=?",Long.class,companyId); }
    private AccountInput normalizeAccount(AccountInput in,String provider) {
        Sender s=in.sender()==null?new Sender("","","","","",""):in.sender();
        s=new Sender(text(s.name(),30,"发件人",false),text(s.phone(),30,"发件电话",false),text(s.province(),30,"发件省份",false),
            text(s.city(),30,"发件城市",false),text(s.county(),30,"发件区县",false),text(s.address(),100,"发件地址",false));
        String url=provider.equals("MANUAL")?"":clean(in.orderUrl());
        if(provider.equals("ANE")) { if(url.isEmpty()) url=expectedOrderUrl(); validateOrderUrl(url); }
        return new AccountInput(in.version(),text(in.code(),100,"接入编码",false),text(in.customerCode(),30,"客户编码",false),text(in.dictId(),30,"网点编码",false),
            text(in.appKey(),500,"应用密钥",false),text(in.customerPass(),30,"客户密钥",false),url,s);
    }
    public String expectedOrderUrl() { return "https://"+(environment.equals("production")?"opc.ane56.com":"opc.test.ane56.com")+"/aneop/opwb/lb/new"; }
    public void validateOrderUrl(String value) {
        try {
            URI u=URI.create(value);
            URI expected=URI.create(expectedOrderUrl());
            if(!"https".equals(u.getScheme()) || !expected.getHost().equals(u.getHost()) || !expected.getPath().equals(u.getRawPath())
                    ||u.getPort()!=-1||u.getUserInfo()!=null||u.getRawQuery()!=null||u.getRawFragment()!=null) throw new IllegalArgumentException();
        } catch(Exception e) { throw invalid("安能接口地址必须使用当前环境的固定HTTPS地址"); }
    }
    private boolean ready(String provider,AccountInput c,boolean app,boolean pass) {
        if(!"ANE".equals(provider)||!app||!pass) return false;
        Sender s=c.sender();
        return s!=null && !clean(c.code()).isEmpty()&&!clean(c.customerCode()).isEmpty()&&!clean(c.dictId()).isEmpty()
                &&!clean(s.name()).isEmpty()&&!clean(s.phone()).isEmpty()&&ProvinceCatalog.normalize(s.province())!=null
                &&!clean(s.city()).isEmpty()&&!clean(s.county()).isEmpty()&&!clean(s.address()).isEmpty();
    }
    private AccountInput publicConfig(AccountInput c) { return new AccountInput(c.version(),c.code(),c.customerCode(),c.dictId(),null,null,c.orderUrl(),c.sender()); }
    public List<Template> templates(long companyId) {
        company(companyId);
        return jdbc.query("select * from logistics_template where company_id=? and environment=? order by id",this::templateRow,companyId,environment);
    }
    public Template template(long id) {
        return jdbc.query("select * from logistics_template where id=? and environment=?",this::templateRow,id,environment).stream().findFirst().orElseThrow(()->missing("运费模板不存在"));
    }
    Template lockTemplate(long id) {
        return jdbc.query("select * from logistics_template where id=? and environment=? for update",this::templateRow,id,environment).stream().findFirst().orElseThrow(()->missing("运费模板不存在"));
    }
    @Transactional public Template createTemplate(long companyId,TemplateInput input) {
        lockCompany(companyId); TemplateInput c=validateTemplate(input); Long account=currentAccountId(companyId); Instant now=clock.instant();
        String slot=c.status().equals("enabled")?slot(c,account):null;
        try {
            long id=insert("insert into logistics_template(company_id,environment,account_id,version_no,status,active_slot,config_json,created_at,updated_at) values(?,?,?,0,?,?,?,?,?)",
                companyId,environment,account,c.status(),slot,json(c),ts(now),ts(now));
            insert("insert into logistics_template_version(template_id,version_no,account_id,config_json,created_at) values(?,0,?,?,?)",id,account,json(c),ts(now));
            return template(id);
        } catch(DuplicateKeyException e) { throw duplicate("同一账号、产品和始发地只能启用一个模板"); }
    }
    @Transactional public Template updateTemplate(long id,TemplateInput input) {
        Template observed=template(id); lockCompany(observed.companyId()); Template old=template(id);
        if(input==null) throw invalid("请填写模板"); expected(input.version(),old.version()); TemplateInput c=validateTemplate(input);
        long next=old.version()+1; c=new TemplateInput(c.name(),c.productCode(),c.productName(),c.originProvince(),c.originCity(),c.originCounty(),c.rules(),c.status(),c.remark(),next);
        Long account=c.status().equals("enabled")?currentAccountId(old.companyId()):old.accountId(); Instant now=clock.instant();
        try {
            jdbc.update("update logistics_template set account_id=?,version_no=?,status=?,active_slot=?,config_json=?,updated_at=? where id=?",
                account,next,c.status(),c.status().equals("enabled")?slot(c,account):null,json(c),ts(now),id);
            insert("insert into logistics_template_version(template_id,version_no,account_id,config_json,created_at) values(?,?,?,?,?)",id,next,account,json(c),ts(now));
            return template(id);
        } catch(DuplicateKeyException e) { throw duplicate("同一账号、产品和始发地只能启用一个模板"); }
    }
    @Transactional public Template templateStatus(long id,StatusInput input) {
        Template old=template(id); if(input==null||input.version()==null) throw invalid("缺少模板版本");
        return updateTemplate(id,new TemplateInput(old.name(),old.productCode(),old.productName(),old.originProvince(),old.originCity(),old.originCounty(),old.rules(),input.status(),old.remark(),input.version()));
    }
    private TemplateInput validateTemplate(TemplateInput c) {
        if(c==null) throw invalid("请填写运费模板"); String status=clean(c.status()).isEmpty()?"disabled":clean(c.status()); status(status);
        boolean publishing=status.equals("enabled");
        String province=clean(c.originProvince()); if(!province.isEmpty()) { province=ProvinceCatalog.normalize(province); if(province==null) throw invalid("始发省份无效"); }
        String city=text(c.originCity(),30,"始发城市",false),county=text(c.originCounty(),30,"始发区县",false);
        if(publishing && (province.isEmpty()||city.isEmpty()||county.isEmpty())) throw invalid("启用模板必须填写完整始发省市区");
        String name=text(c.name(),100,"模板名称",true),product=text(c.productCode(),50,"产品编码",publishing),productName=text(c.productName(),100,"产品名称",publishing);
        if(c.rules()!=null || publishing) calculator.validateRules(c.rules(),publishing);
        return new TemplateInput(name,product,productName,province,city,county,c.rules(),status,text(c.remark(),1000,"备注",false),c.version());
    }
    private String slot(TemplateInput c,Long account) { return hash((account==null?"0":account.toString())+"|"+c.productCode()+"|"+c.originProvince()+"|"+c.originCity()+"|"+c.originCounty()); }
    private Template templateRow(ResultSet r,int n) throws SQLException {
        TemplateInput c=read(r.getString("config_json"),TemplateInput.class);
        return new Template(r.getLong("id"),r.getLong("company_id"),r.getObject("account_id",Long.class),c.name(),c.productCode(),c.productName(),c.originProvince(),c.originCity(),c.originCounty(),c.rules(),r.getString("status"),c.remark(),r.getLong("version_no"),r.getTimestamp("updated_at").toInstant());
    }
    private Company companyRow(ResultSet r,int n) throws SQLException { return new Company(r.getLong("id"),r.getString("code"),r.getString("name"),r.getString("provider"),r.getString("status"),r.getString("contact_name"),r.getString("contact_phone"),r.getInt("sort_order"),r.getString("remark"),r.getLong("version_no"),r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant()); }
    List<Company> enabledCompanies() { return jdbc.query("select * from logistics_company where status='enabled' order by sort_order,id",this::companyRow); }
    private long insert(String sql,Object... values) {
        var keys=new GeneratedKeyHolder();
        jdbc.update(connection->{ var statement=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS); for(int i=0;i<values.length;i++) statement.setObject(i+1,values[i]); return statement; },keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
    String json(Object value) { try { return mapper.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException("物流资料无法序列化"); } }
    <T> T read(String value,Class<T> type) { try { return mapper.readValue(value,type); } catch(Exception e) { throw new IllegalStateException("物流资料无法读取"); } }
    static String clean(String s) { return s==null?"":s.strip(); }
    static Timestamp ts(Instant i) { return Timestamp.from(i); }
    static void expected(Long requested,long actual) { if(requested==null) throw invalid("缺少资料版本，请重新打开后保存"); if(requested!=actual) throw conflict("LOGISTICS_VERSION_CONFLICT","资料已被修改，请刷新后重试"); }
    static void status(String value) { if(!Set.of("enabled","disabled").contains(clean(value))) throw invalid("状态无效"); }
    static void page(int page,int size) { if(page<1||size<1||size>100) throw invalid("分页参数无效"); }
    static String text(String s,int max,String label,boolean required) { String value=clean(s); if((required&&value.isEmpty())||value.length()>max) throw invalid(label+"无效或长度超过限制"); return value; }
    static BusinessException invalid(String message) { return new BusinessException("VALIDATION_FAILED",HttpStatus.BAD_REQUEST,message); }
    static BusinessException missing(String message) { return new BusinessException("LOGISTICS_NOT_FOUND",HttpStatus.NOT_FOUND,message); }
    static BusinessException conflict(String code,String message) { return new BusinessException(code,HttpStatus.CONFLICT,message); }
    static BusinessException duplicate(String message) { return conflict("LOGISTICS_DUPLICATE",message); }
    private static String context(long id,String env,long version) { return "logistics-account:"+id+":"+env+":"+version; }
    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
}
