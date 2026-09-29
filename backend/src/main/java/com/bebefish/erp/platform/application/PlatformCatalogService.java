package com.bebefish.erp.platform.application;
import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.platform.api.PlatformDtos;
import com.bebefish.erp.platform.domain.*;
import com.bebefish.erp.platform.infrastructure.JdbcPlatformCatalogRepository;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class PlatformCatalogService {
    private final JdbcPlatformCatalogRepository repository;
    public PlatformCatalogService(JdbcPlatformCatalogRepository repository) { this.repository=repository; }
    public Platform getPlatform(long id) { return platform(id,false); }
    public PlatformShop getShop(long id) { return shop(id,false); }
    public Page<Platform> listPlatforms(String keyword,String status,int page,int size) { query(keyword,status,page,size); return repository.platforms(clean(keyword),clean(status),page,size); }
    public Page<PlatformShop> listShops(String keyword,String status,Long platformId,int page,int size) {
        query(keyword,status,page,size); if(platformId!=null && platformId<=0) throw invalid("平台参数无效");
        return repository.shops(clean(keyword),clean(status),platformId,page,size);
    }
    @Transactional public Platform createPlatform(PlatformDtos.Create c,String operator) {
        validate(c.code(),c.name(),c.sortOrder(),c.remark(),false);
        try { return getPlatform(repository.insert(false,null,internalCode(false,c.code()),clean(c.name()),sort(c.sortOrder()),clean(c.remark()),null,null,null,operator)); }
        catch(DuplicateKeyException e) { throw duplicate(e,false); }
    }
    @Transactional public PlatformShop createShop(PlatformDtos.Create c,String operator) {
        validate(c.code(),c.name(),c.sortOrder(),c.remark(),true);
        validateShopDetails(c.channelType(),c.ownerName(),c.optionLabelOverride());
        if(c.platformId()==null || c.platformId()<=0) throw invalid("请选择所属平台");
        enabled(platform(c.platformId(),true));
        try { return getShop(repository.insert(true,c.platformId(),internalCode(true,c.code()),clean(c.name()),sort(c.sortOrder()),clean(c.remark()),
            c.channelType(),clean(c.ownerName()),override(c.optionLabelOverride()),operator)); }
        catch(DuplicateKeyException e) { throw duplicate(e,true); }
    }
    @Transactional public Platform updatePlatform(long id,PlatformDtos.Update c,String operator) {
        validate(null,c.name(),c.sortOrder(),c.remark(),false); platform(id,true); version(c.version());
        try { if(!repository.update(false,id,clean(c.name()),sort(c.sortOrder()),clean(c.remark()),c.version(),null,null,null,operator)) throw conflict(false); return getPlatform(id); }
        catch(DuplicateKeyException e) { throw duplicate(e,false); }
    }
    @Transactional public PlatformShop updateShop(long id,PlatformDtos.Update c,String operator) {
        validate(null,c.name(),c.sortOrder(),c.remark(),true); validateShopDetails(c.channelType(),c.ownerName(),c.optionLabelOverride()); lockShop(id); version(c.version());
        try { if(!repository.update(true,id,clean(c.name()),sort(c.sortOrder()),clean(c.remark()),c.version(),
            c.channelType(),clean(c.ownerName()),override(c.optionLabelOverride()),operator)) throw conflict(true); return getShop(id); }
        catch(DuplicateKeyException e) { throw duplicate(e,true); }
    }
    @Transactional public Platform changePlatformStatus(long id,String state,long version,String operator) {
        status(state); platform(id,true); version(version);
        if(!repository.status(false,id,state,version,operator)) throw conflict(false); return getPlatform(id);
    }
    @Transactional public PlatformShop changeShopStatus(long id,String state,long version,String operator) {
        status(state); var s=lockShop(id); version(version);
        if("enabled".equals(state)) enabled(platform(s.platformId(),true));
        if(!repository.status(true,id,state,version,operator)) throw conflict(true); return getShop(id);
    }
    private PlatformShop lockShop(long id) { var s=shop(id,false); platform(s.platformId(),true); return shop(id,true); }
    public Platform platform(long id,boolean lock) { return repository.platform(id,lock).orElseThrow(()->new BusinessException("PLATFORM_NOT_FOUND",HttpStatus.NOT_FOUND,"平台不存在")); }
    public PlatformShop shop(long id,boolean lock) { return repository.shop(id,lock).orElseThrow(()->new BusinessException("PLATFORM_SHOP_NOT_FOUND",HttpStatus.NOT_FOUND,"店铺不存在")); }
    public static void enabled(Platform p) { if(!"enabled".equals(p.status())) throw new BusinessException("PLATFORM_DISABLED",HttpStatus.CONFLICT,"平台已停用，请刷新选项"); }
    private void validate(String code,String name,Integer sort,String remark,boolean shop) {
        if(code!=null && !code.isBlank() && !code(code).matches("[A-Z0-9_-]{1,50}")) throw invalid("编码只能包含字母、数字、下划线和短横线，最多50字");
        if(clean(name).isEmpty() || clean(name).length()>(shop?200:100)) throw invalid("名称不能为空或超过长度限制");
        if(sort(sort)<0 || sort(sort)>9999 || clean(remark).length()>1000) throw invalid("排序或备注超出范围");
    }
    private void validateShopDetails(String channelType,String ownerName,String optionLabelOverride) {
        if(!Set.of("ecommerce","private").contains(channelType==null?"":channelType)
            || clean(ownerName).isEmpty() || clean(ownerName).length()>100
            || (optionLabelOverride!=null && (!"private".equals(channelType) || clean(optionLabelOverride).isEmpty() || clean(optionLabelOverride).length()>200)))
            throw invalid("请选择渠道类型并填写负责人；私域例外标签最多200字");
    }
    private void query(String keyword,String status,int page,int size) {
        if(page<1 || size<1 || size>100 || clean(keyword).length()>200) throw invalid("查询参数无效");
        if(!clean(status).isEmpty()) status(status);
    }
    private void status(String status) { if(status==null || !Set.of("enabled","disabled").contains(status)) throw invalid("状态无效"); }
    private void version(Long version) { if(version==null || version<0) throw invalid("缺少有效版本，请刷新"); }
    private BusinessException duplicate(DuplicateKeyException e,boolean shop) {
        boolean code=e.getMessage()!=null && e.getMessage().contains(shop?"uk_shop_code":"uk_platform_code");
        return new BusinessException((shop?"PLATFORM_SHOP_":"PLATFORM_")+(code?"CODE":"NAME")+"_DUPLICATE",HttpStatus.CONFLICT,(code?"编码":"名称")+"已存在");
    }
    private BusinessException conflict(boolean shop) { return new BusinessException(shop?"PLATFORM_SHOP_VERSION_CONFLICT":"PLATFORM_VERSION_CONFLICT",HttpStatus.CONFLICT,"资料已更新，请刷新核对；当前填写内容已保留"); }
    private static BusinessException invalid(String message) { return new BusinessException("VALIDATION_FAILED",HttpStatus.BAD_REQUEST,message); }
    private static int sort(Integer s) { return s==null?0:s; }
    private static String clean(String s) { return s==null?"":s.strip(); }
    private static String override(String s) { return s==null?null:clean(s); }
    private static String code(String s) { return clean(s).toUpperCase(Locale.ROOT); }
    private static String internalCode(boolean shop,String supplied) {
        return clean(supplied).isEmpty() ? (shop?"S_":"P_")+UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT) : code(supplied);
    }
}
