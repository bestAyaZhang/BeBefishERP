package com.bebefish.erp.shipping.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class YimiOrderDraftTest {
    private final ObjectMapper mapper=new ObjectMapper();
    @Test void yimiOptionsSurviveNormalizationAndStoredJsonRoundTrip() throws Exception {
        var draft=mapper.readValue("""
            {"cargoName":" 商品 ","packType":"纸箱","weight":12,"volume":0.12,"pieceAmount":1,
             "productTypeId":524,"goodsType":179,"payType":104,"logisticsRemark":" 备注 ",
             "yimi":{"visitDelivery":0,"deliveryFlag":3,"floorNo":" 2 "}}
            """,AneOrderDraft.class).normalized();
        var json=mapper.readTree(mapper.writeValueAsString(draft));
        assertThat(json.path("cargoName").asText()).isEqualTo("商品");
        assertThat(json.path("yimi").path("visitDelivery").asInt(-1)).isZero();
        assertThat(json.path("yimi").path("deliveryFlag").asInt()).isEqualTo(3);
        assertThat(json.path("yimi").path("floorNo").asText()).isEqualTo("2");
    }
    @Test void originalAneConstructorAndHistoricalJsonRemainCompatible() throws Exception {
        var old=new AneOrderDraft("商品","纸箱",BigDecimal.ONE,new BigDecimal("0.01"),1,524,180,102,"");
        var restored=mapper.readValue(mapper.writeValueAsString(old),AneOrderDraft.class);
        assertThat(restored.normalized()).isEqualTo(old);
        assertThat(mapper.readTree(mapper.writeValueAsString(restored)).path("yimi").isNull()).isTrue();
    }
}
