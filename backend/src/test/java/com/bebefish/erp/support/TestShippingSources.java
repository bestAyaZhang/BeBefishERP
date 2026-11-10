package com.bebefish.erp.support;
import com.bebefish.erp.platform.application.*;
import com.bebefish.erp.platform.domain.*;
import java.time.LocalDateTime;
import static org.mockito.Mockito.*;
public final class TestShippingSources {
    public static ShippingSourceResolver resolver() {
        var catalog=mock(PlatformCatalogService.class);
        var now=LocalDateTime.of(2026,9,29,0,0);
        var platform=new Platform(1,"TAOBAO","淘宝","enabled",0,"",0,"test","test",now,now,1);
        var shop=new PlatformShop(1,1,"SHOP","贝贝鱼淘宝旗舰店","enabled",0,"",0,"test","test",now,now,"淘宝","enabled");
        when(catalog.platform(1L,true)).thenReturn(platform);
        when(catalog.shop(1L,true)).thenReturn(shop);
        when(catalog.shop(1L,false)).thenReturn(shop);
        when(catalog.getPlatform(1L)).thenReturn(platform);
        when(catalog.getShop(1L)).thenReturn(shop);
        return new ShippingSourceResolver(catalog);
    }
}
