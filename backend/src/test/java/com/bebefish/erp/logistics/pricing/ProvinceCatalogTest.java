package com.bebefish.erp.logistics.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProvinceCatalogTest {
    @Test
    void resolvesCatalogCodesCanonicalNamesAndExplicitShortNames() {
        assertThat(ProvinceCatalog.normalize("330000")).isEqualTo("330000");
        assertThat(ProvinceCatalog.normalize(" 浙江省 ")).isEqualTo("330000");
        assertThat(ProvinceCatalog.normalize("浙江")).isEqualTo("330000");
        assertThat(ProvinceCatalog.normalize("内蒙古自治区")).isEqualTo("150000");
        assertThat(ProvinceCatalog.normalize("广西")).isEqualTo("450000");
        assertThat(ProvinceCatalog.normalize("宁夏回族自治区")).isEqualTo("640000");
        assertThat(ProvinceCatalog.normalize("香港特别行政区")).isEqualTo("810000");
        assertThat(ProvinceCatalog.normalize("澳门")).isEqualTo("820000");
        assertThat(ProvinceCatalog.normalize("台湾省")).isEqualTo("710000");
    }

    @Test
    void rejectsUnknownAmbiguousCityAndPartialProvinceText() {
        for (var input : new String[]{null, "", "  ", "999999", "江", "华东", "杭州市", "浙江省杭州市", "330100"}) {
            assertThat(ProvinceCatalog.normalize(input)).isNull();
        }
    }

    @Test
    void exposesCompleteDistinctGroupedCatalogWhoseNamesRoundTrip() {
        var options = ProvinceCatalog.options();
        assertThat(options).hasSize(34);
        assertThat(options).extracting(ProvinceCatalog.Province::code).doesNotHaveDuplicates();
        for (var option : options) {
            assertThat(option.code()).matches("\\d{6}");
            assertThat(option.name()).isNotBlank();
            assertThat(option.group()).isNotBlank();
            assertThat(ProvinceCatalog.normalize(option.name())).isEqualTo(option.code());
        }
    }
}
