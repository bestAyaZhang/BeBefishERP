package com.bebefish.erp.shipping.application;
import com.bebefish.erp.shipping.api.ShippingFormOptions;
import com.bebefish.erp.shipping.infrastructure.JdbcShippingFormOptionsRepository;
import com.bebefish.erp.platform.infrastructure.JdbcPlatformCatalogRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true)
public class ShippingFormOptionsService {
    private final JdbcShippingFormOptionsRepository repository;
    private final JdbcPlatformCatalogRepository catalog;
    public ShippingFormOptionsService(JdbcShippingFormOptionsRepository repository,JdbcPlatformCatalogRepository catalog) {
        this.repository=repository; this.catalog=catalog;
    }
    public ShippingFormOptions options() {
        var shops=catalog.allShops(true);
        return new ShippingFormOptions(shops.stream().map(s->s.name()).distinct().toList(),repository.findActivePreparers(),
            catalog.allPlatforms(true).stream().map(p->new ShippingFormOptions.PlatformOption(p.id(),p.name())).toList(),
            shops.stream().map(s->new ShippingFormOptions.ShopOption(s.id(),s.platformId(),s.name(),
                s.optionLabel()==null || s.optionLabel().isBlank()?s.name():s.optionLabel())).toList());
    }
    public record FilterPlatform(long id,String name,String status) {}
    public record FilterShop(long id,long platformId,String name,String optionLabel,String status,String platformStatus) {}
    public record FilterOptions(List<FilterPlatform> platforms,List<FilterShop> shops) {}
    public FilterOptions filterOptions() {
        return new FilterOptions(catalog.allPlatforms(false).stream().map(p->new FilterPlatform(p.id(),p.name(),p.status())).toList(),
            catalog.allShops(false).stream().map(s->new FilterShop(s.id(),s.platformId(),s.name(),
                s.optionLabel()==null || s.optionLabel().isBlank()?s.name():s.optionLabel(),s.status(),s.platformStatus())).toList());
    }
}
