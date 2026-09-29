package com.bebefish.erp.platform.infrastructure;
import com.bebefish.erp.platform.domain.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPlatformCatalogRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcPlatformCatalogRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
    private static final String PLATFORM = "select p.*, (select count(*) from platform_shop s where s.platform_id=p.id) shop_count from sales_platform p";
    private static final String SHOP = "select s.*,p.platform_name,p.status platform_status from platform_shop s join sales_platform p on p.id=s.platform_id";
    public Optional<Platform> platform(long id, boolean lock) {
        if(lock) jdbc.queryForList("select id from sales_platform where id=:id for update",Map.of("id",id));
        return jdbc.query(PLATFORM+" where p.id=:id"+(lock?" for update":""),Map.of("id",id),this::platformRow).stream().findFirst();
    }
    public Optional<PlatformShop> shop(long id, boolean lock) {
        if(lock) jdbc.queryForList("select id from platform_shop where id=:id for update",Map.of("id",id));
        return jdbc.query(SHOP+" where s.id=:id"+(lock?" for update":""),Map.of("id",id),this::shopRow).stream().findFirst();
    }
    public Page<Platform> platforms(String keyword,String status,int page,int size) {
        var params=parameters(keyword,status,page,size);
        String where=where("p","platform",status);
        long count=jdbc.queryForObject("select count(*) from sales_platform p"+where,params,Long.class);
        return new PageImpl<>(jdbc.query(PLATFORM+where+" order by p.sort_order,p.id limit :limit offset :offset",params,this::platformRow),PageRequest.of(page-1,size),count);
    }
    public Page<PlatformShop> shops(String keyword,String status,Long platformId,int page,int size) {
        var params=parameters(keyword,status,page,size).addValue("platformId",platformId);
        String where=where("s","shop",status)+(platformId==null?"":" and s.platform_id=:platformId");
        long count=jdbc.queryForObject("select count(*) from platform_shop s"+where,params,Long.class);
        return new PageImpl<>(jdbc.query(SHOP+where+" order by s.sort_order,s.id limit :limit offset :offset",params,this::shopRow),PageRequest.of(page-1,size),count);
    }
    public List<Platform> allPlatforms(boolean activeOnly) {
        return jdbc.query(PLATFORM+(activeOnly?" where p.status='enabled'":"")+" order by p.sort_order,p.id",Map.of(),this::platformRow);
    }
    public List<PlatformShop> allShops(boolean activeOnly) {
        return jdbc.query(SHOP+(activeOnly?" where s.status='enabled' and p.status='enabled'":"")+" order by s.sort_order,s.id",Map.of(),this::shopRow);
    }
    public long insert(boolean shop,Long parent,String code,String name,int sort,String remark,
            String channelType,String ownerName,String optionLabel,String operator) {
        String table=shop?"platform_shop":"sales_platform", prefix=shop?"shop":"platform";
        var p=new MapSqlParameterSource().addValue("parent",parent).addValue("code",code).addValue("name",name)
            .addValue("sort",sort).addValue("remark",remark).addValue("channelType",channelType)
            .addValue("ownerName",ownerName).addValue("optionLabel",optionLabel).addValue("operator",operator);
        var keys=new GeneratedKeyHolder();
        jdbc.update("insert into "+table+" ("+(shop?"platform_id,":"")+prefix+"_code,"+prefix+"_name,status,sort_order,remark"+
            (shop?",channel_type,owner_name,option_label":"")+",version_no,created_by,updated_by,created_at,updated_at) values ("+
            (shop?":parent,":"")+":code,:name,'enabled',:sort,:remark"+(shop?",:channelType,:ownerName,:optionLabel":"")+
            ",0,:operator,:operator,now(3),now(3))",p,keys,new String[]{"id"});
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
    public boolean update(boolean shop,long id,String name,int sort,String remark,long version,
            String channelType,String ownerName,String optionLabel,String operator) {
        String table=shop?"platform_shop":"sales_platform", prefix=shop?"shop":"platform";
        var params=new MapSqlParameterSource().addValue("id",id).addValue("name",name).addValue("sort",sort)
            .addValue("remark",remark).addValue("version",version).addValue("channelType",channelType)
            .addValue("ownerName",ownerName).addValue("optionLabel",optionLabel).addValue("operator",operator);
        return jdbc.update("update "+table+" set "+prefix+"_name=:name,sort_order=:sort,remark=:remark"+
            (shop?",channel_type=:channelType,owner_name=:ownerName,option_label=case when :channelType='private' then coalesce(:optionLabel,option_label) else null end":"")+
            ",version_no=version_no+1,updated_by=:operator,updated_at=now(3) where id=:id and version_no=:version",params)==1;
    }
    public boolean status(boolean shop,long id,String status,long version,String operator) {
        return jdbc.update("update "+(shop?"platform_shop":"sales_platform")+" set status=:status,version_no=version_no+1,updated_by=:operator,updated_at=now(3) where id=:id and version_no=:version",
            Map.of("id",id,"status",status,"version",version,"operator",operator))==1;
    }
    private MapSqlParameterSource parameters(String keyword,String status,int page,int size) {
        return new MapSqlParameterSource().addValue("q","%"+keyword.replace("!","!!").replace("%","!%").replace("_","!_")+"%")
            .addValue("status",status).addValue("limit",size).addValue("offset",(long)(page-1)*size);
    }
    private String where(String a,String prefix,String status) {
        return " where ("+a+"."+prefix+"_code like :q escape '!' or "+a+"."+prefix+"_name like :q escape '!')"+(status.isEmpty()?"":" and "+a+".status=:status");
    }
    private Platform platformRow(ResultSet r,int row) throws SQLException {
        return new Platform(r.getLong("id"),r.getString("platform_code"),r.getString("platform_name"),r.getString("status"),
            r.getInt("sort_order"),r.getString("remark"),r.getLong("version_no"),r.getString("created_by"),r.getString("updated_by"),
            r.getTimestamp("created_at").toLocalDateTime(),r.getTimestamp("updated_at").toLocalDateTime(),r.getLong("shop_count"));
    }
    private PlatformShop shopRow(ResultSet r,int row) throws SQLException {
        return new PlatformShop(r.getLong("id"),r.getLong("platform_id"),r.getString("shop_code"),r.getString("shop_name"),r.getString("status"),
            r.getInt("sort_order"),r.getString("remark"),r.getLong("version_no"),r.getString("created_by"),r.getString("updated_by"),
            r.getTimestamp("created_at").toLocalDateTime(),r.getTimestamp("updated_at").toLocalDateTime(),r.getString("platform_name"),r.getString("platform_status"),
            r.getString("channel_type"),r.getString("owner_name"),
            ShopOptionLabel.generate(r.getString("channel_type"),r.getString("platform_name"),r.getString("shop_name"),r.getString("option_label")));
    }
}
