package com.bebefish.erp.platform.application;
import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.shipping.domain.ShipmentSource;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ShippingSourceResolver {
    private final PlatformCatalogService catalog;
    public ShippingSourceResolver(PlatformCatalogService catalog) { this.catalog=catalog; }
    public ShipmentSource resolveForCreate(Long platformId,Long shopId) {
        if(platformId==null || shopId==null || platformId<=0 || shopId<=0)
            throw new BusinessException("SHIPMENT_SOURCE_SELECTION_REQUIRED",HttpStatus.BAD_REQUEST,"请刷新页面并选择平台和店铺");
        if(catalog.shop(shopId,false).platformId()!=platformId) throw new BusinessException("PLATFORM_SHOP_MISMATCH",HttpStatus.BAD_REQUEST,"店铺不属于所选平台");
        var p=catalog.platform(platformId,true);
        var s=catalog.shop(shopId,true);
        PlatformCatalogService.enabled(p);
        if(!"enabled".equals(s.status())) throw new BusinessException("PLATFORM_SHOP_DISABLED",HttpStatus.CONFLICT,"店铺已停用，请刷新选项");
        return new ShipmentSource(p.id(),s.id(),p.name(),s.name());
    }
    public ShipmentSource resolveForUpdate(ShipmentSource previous,Long platformId,Long shopId) {
        if(Objects.equals(previous.platformId(),platformId) && Objects.equals(previous.shopId(),shopId)) return previous;
        return resolveForCreate(platformId,shopId);
    }
    public void validateFilter(Long platformId,Long shopId) {
        if(platformId!=null) catalog.getPlatform(platformId);
        if(shopId!=null) {
            var s=catalog.getShop(shopId);
            if(platformId!=null && s.platformId()!=platformId) throw new BusinessException("PLATFORM_SHOP_MISMATCH",HttpStatus.BAD_REQUEST,"店铺不属于所选平台");
        }
    }
}
