package com.bebefish.erp.shipping.application;
import com.bebefish.erp.shipping.infrastructure.JdbcShippingFormOptionsRepository;
import com.bebefish.erp.platform.infrastructure.JdbcPlatformCatalogRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class ShippingFormOptionsServiceTest {
    @Test void emptyCatalogStaysEmptyWithoutConfigurationFallback() {
        var people=mock(JdbcShippingFormOptionsRepository.class);
        var catalog=mock(JdbcPlatformCatalogRepository.class);
        when(people.findActivePreparers()).thenReturn(List.of());
        when(catalog.allShops(true)).thenReturn(List.of());
        when(catalog.allPlatforms(true)).thenReturn(List.of());
        var options=new ShippingFormOptionsService(people,catalog).options();
        assertThat(options.shopNames()).isEmpty(); assertThat(options.shops()).isEmpty(); assertThat(options.platforms()).isEmpty();
    }
}