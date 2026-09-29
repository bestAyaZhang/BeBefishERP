package com.bebefish.erp.platform.migration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bebefish.erp.platform.migration.CatalogMigrationManifest.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Supplier;

/** Offline only: no component annotation, no application startup hook. */
public final class CatalogMigrationService {
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactions;
    private final ObjectMapper mapper;
    public CatalogMigrationService(JdbcTemplate jdbc,PlatformTransactionManager transactions,ObjectMapper mapper) {
        this.jdbc=jdbc; this.transactions=transactions; this.mapper=mapper;
    }
    public record Preview(CatalogMigrationManifest manifest,List<String> configuredShopNames,long shipmentCount,long unlinkedCount) {}
    public record Result(long updated,long skipped,long conflicts) {}
    private record Pair(String platform,String shop) {}
    private record Outcome(long id,String status,Long oldPlatformId,Long oldShopId,Long platformId,Long shopId,long oldVersion,long newVersion) {}
    public Preview dryRun(List<String> configuredShops) {
        var tx=new TransactionTemplate(transactions); tx.setReadOnly(true);
        return tx.execute(ignored -> {
            boolean linked=hasColumn("shipment","platform_id");
            String columns=linked?"platform_id,shop_id":"null platform_id,null shop_id";
            var rows=jdbc.query("select id,version_no,platform,shop_name,"+columns+" from shipment order by id",(r,n)->
                new ShipmentBaseline(r.getLong("id"),r.getLong("version_no"),r.getString("platform"),r.getString("shop_name"),r.getObject("platform_id",Long.class),r.getObject("shop_id",Long.class)));
            var pairs=new LinkedHashSet<Pair>();
            rows.stream().filter(r->r.shopId()==null).forEach(r->pairs.add(new Pair(r.rawPlatform(),r.rawShopName())));
            var m=new CatalogMigrationManifest(1,UUID.randomUUID().toString(),database(),rows.stream().mapToLong(ShipmentBaseline::id).max().orElse(0),
                List.of(),List.of(),pairs.stream().map(p->new Mapping(p.platform(),p.shop(),null,null)).toList(),rows);
            return new Preview(m,configuredShops.stream().filter(Objects::nonNull).map(String::strip).filter(s->!s.isEmpty()).distinct().toList(),
                rows.size(),rows.stream().filter(r->r.shopId()==null).count());
        });
    }
    public Result apply(CatalogMigrationManifest m,Path output) throws IOException {
        validate(m);
        if(!hasColumn("shipment","shop_id")) throw new IllegalStateException("请先安装平台目录结构迁移");
        Files.createDirectory(output); // Never overwrite an earlier attempt's evidence.
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("before.json").toFile(),m);
        Path log=output.resolve("results.jsonl"); Files.createFile(log);
        var targets=transaction(()->seed(m));
        long updated=0,skipped=0,conflicts=0;
        var mappings=new HashMap<Pair,Mapping>(); for(var x:m.mappings()) mappings.put(new Pair(x.rawPlatform(),x.rawShopName()),x);
        for(var b:m.shipmentBaselines()) {
            Mapping mapping=mappings.get(new Pair(b.rawPlatform(),b.rawShopName()));
            Long p=mapping==null || mapping.platformCode()==null?null:targets.get("p:"+mapping.platformCode());
            Long s=mapping==null || mapping.shopCode()==null?null:targets.get("s:"+mapping.shopCode());
            var result=transaction(()->backfill(m.batchId(),b,p,s));
            Files.writeString(log,mapper.writeValueAsString(result)+System.lineSeparator(),StandardOpenOption.APPEND);
            if("updated".equals(result.status())) updated++; else if("conflict".equals(result.status())) conflicts++; else skipped++;
        }
        var result=new Result(updated,skipped,conflicts);
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("summary.json").toFile(),result);
        return result;
    }
    private Outcome backfill(String batch,ShipmentBaseline b,Long targetPlatform,Long targetShop) {
        if(targetPlatform==null) return outcome(b,"unmapped",b.platformId(),b.shopId(),b.version());
        var rows=jdbc.query("select id,version_no,platform,shop_name,platform_id,shop_id from shipment where id=? for update",
            (r,n)->new ShipmentBaseline(r.getLong("id"),r.getLong("version_no"),r.getString("platform"),r.getString("shop_name"),r.getObject("platform_id",Long.class),r.getObject("shop_id",Long.class)),b.id());
        if(rows.isEmpty()) return outcome(b,"conflict",targetPlatform,targetShop,b.version());
        var current=rows.getFirst();
        if(!Objects.equals(current.rawPlatform(),b.rawPlatform()) || !Objects.equals(current.rawShopName(),b.rawShopName())) return outcome(b,"conflict",targetPlatform,targetShop,current.version());
        if(Objects.equals(current.platformId(),targetPlatform) && Objects.equals(current.shopId(),targetShop)) return outcome(b,"skipped",targetPlatform,targetShop,current.version());
        if(current.version()!=b.version() || !Objects.equals(current.platformId(),b.platformId()) || !Objects.equals(current.shopId(),b.shopId())
            || (current.platformId()!=null && !Objects.equals(current.platformId(),targetPlatform)) || current.shopId()!=null)
            return outcome(b,"conflict",targetPlatform,targetShop,current.version());
        jdbc.queryForList("select id from sales_platform where id=? for update",targetPlatform);
        if(targetShop!=null) {
            var parent=jdbc.queryForObject("select platform_id from platform_shop where id=? for update",Long.class,targetShop);
            if(!Objects.equals(parent,targetPlatform)) throw new IllegalArgumentException("映射店铺不属于目标平台");
        }
        int count=jdbc.update("update shipment set platform_id=?,shop_id=?,version_no=version_no+1,updated_by=?,updated_at=now(3) where id=? and version_no=?",
            targetPlatform,targetShop,"migration:"+batch,b.id(),b.version());
        return outcome(b,count==1?"updated":"conflict",targetPlatform,targetShop,count==1?b.version()+1:b.version());
    }
    private Outcome outcome(ShipmentBaseline b,String state,Long p,Long s,long version) { return new Outcome(b.id(),state,b.platformId(),b.shopId(),p,s,b.version(),version); }
    private Map<String,Long> seed(CatalogMigrationManifest m) {
        var ids=new HashMap<String,Long>();
        for(var p:m.platforms()) {
            var rows=jdbc.queryForList("select * from sales_platform where platform_code=? for update",p.code());
            if(rows.isEmpty()) jdbc.update("insert into sales_platform(platform_code,platform_name,status,sort_order,remark,version_no,created_by,updated_by,created_at,updated_at) values (?,?,?,?,'',0,?,?,now(3),now(3))",
                p.code(),p.name(),p.status(),p.sortOrder(),"migration:"+m.batchId(),"migration:"+m.batchId());
            else same(rows.getFirst(),p.name(),p.status(),p.sortOrder(),"platform_name");
            ids.put("p:"+p.code(),jdbc.queryForObject("select id from sales_platform where platform_code=?",Long.class,p.code()));
        }
        for(var s:m.shops()) {
            long parent=platformId(ids,s.platformCode());
            var rows=jdbc.queryForList("select * from platform_shop where shop_code=? for update",s.code());
            if(rows.isEmpty()) jdbc.update("insert into platform_shop(platform_id,shop_code,shop_name,status,sort_order,remark,version_no,created_by,updated_by,created_at,updated_at) values (?,?,?,?,?,'',0,?,?,now(3),now(3))",
                parent,s.code(),s.name(),s.status(),s.sortOrder(),"migration:"+m.batchId(),"migration:"+m.batchId());
            else { same(rows.getFirst(),s.name(),s.status(),s.sortOrder(),"shop_name"); if(((Number)rows.getFirst().get("platform_id")).longValue()!=parent) throw new IllegalArgumentException("店铺编码归属与清单不一致"); }
            ids.put("s:"+s.code(),jdbc.queryForObject("select id from platform_shop where shop_code=?",Long.class,s.code()));
        }
        for(var x:m.mappings()) {
            if(x.platformCode()==null) continue;
            long p=platformId(ids,x.platformCode());
            if(x.shopCode()!=null) {
                var row=jdbc.queryForMap("select id,platform_id from platform_shop where shop_code=?",x.shopCode());
                if(((Number)row.get("platform_id")).longValue()!=p) throw new IllegalArgumentException("映射店铺归属错误");
                ids.put("s:"+x.shopCode(),((Number)row.get("id")).longValue());
            }
        }
        return ids;
    }
    private long platformId(Map<String,Long> ids,String code) {
        return ids.computeIfAbsent("p:"+code,k->jdbc.queryForObject("select id from sales_platform where platform_code=?",Long.class,code));
    }
    private void same(Map<String,Object> row,String name,String state,int sort,String field) {
        if(!Objects.equals(row.get(field),name) || !Objects.equals(row.get("status"),state) || ((Number)row.get("sort_order")).intValue()!=sort)
            throw new IllegalArgumentException("已存在编码的资料与清单不一致，未覆盖");
    }
    private void validate(CatalogMigrationManifest m) {
        if(m==null || m.schemaVersion()!=1 || m.batchId()==null || !m.batchId().matches("[A-Za-z0-9_-]{1,50}")
            || !Objects.equals(database(),m.targetDatabase()) || m.baselineMaxShipmentId()<0
            || m.platforms()==null || m.shops()==null || m.mappings()==null || m.shipmentBaselines()==null)
            throw new IllegalArgumentException("迁移清单版本、批次或目标数据库无效");
        var pc=new HashSet<String>(); var sc=new HashSet<String>(); var pairs=new HashSet<Pair>(); var rowIds=new HashSet<Long>();
        for(var p:m.platforms()) { fields(p.code(),p.name(),p.status(),p.sortOrder(),100); if(!pc.add(p.code())) throw new IllegalArgumentException("重复平台编码"); }
        for(var s:m.shops()) { fields(s.code(),s.name(),s.status(),s.sortOrder(),200); code(s.platformCode()); if(!sc.add(s.code())) throw new IllegalArgumentException("重复店铺编码"); }
        for(var x:m.mappings()) {
            if(x.rawPlatform()==null || x.rawShopName()==null || !pairs.add(new Pair(x.rawPlatform(),x.rawShopName()))) throw new IllegalArgumentException("映射有歧义或缺少原始名称");
            if(x.platformCode()!=null) code(x.platformCode());
            if(x.shopCode()!=null) { code(x.shopCode()); if(x.platformCode()==null) throw new IllegalArgumentException("店铺映射缺少平台"); }
        }
        for(var b:m.shipmentBaselines()) if(b.id()<=0 || b.id()>m.baselineMaxShipmentId() || b.version()<0 || b.rawPlatform()==null || b.rawShopName()==null || !rowIds.add(b.id())) throw new IllegalArgumentException("单据基线不完整或重复");
    }
    private void fields(String code,String name,String state,int sort,int max) {
        code(code);
        if(name==null || name.isBlank() || !name.equals(name.strip()) || name.length()>max || !Set.of("enabled","disabled").contains(state==null?"":state) || sort<0 || sort>9999) throw new IllegalArgumentException("主数据字段无效");
    }
    private void code(String c) { if(c==null || !c.matches("[A-Z0-9_-]{1,50}")) throw new IllegalArgumentException("编码须为大写字母、数字、下划线或短横线"); }
    private boolean hasColumn(String table,String column) { return jdbc.queryForObject("select count(*) from information_schema.columns where table_schema=database() and table_name=? and column_name=?",Integer.class,table,column)>0; }
    private String database() { return jdbc.queryForObject("select database()",String.class); }
    private <T> T transaction(Supplier<T> work) { return new TransactionTemplate(transactions).execute(status->work.get()); }
}
