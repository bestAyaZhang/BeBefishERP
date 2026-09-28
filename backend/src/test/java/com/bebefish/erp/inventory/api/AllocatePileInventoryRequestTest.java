package com.bebefish.erp.inventory.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AllocatePileInventoryRequestTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void rejectsFractionalUnitsInsteadOfInterpretingThemAsAZeroStockLink() {
        assertThatThrownBy(() -> mapper.readValue(
                "{\"palletId\":\"pile-a\",\"skuId\":101,\"units\":1.5}",
                AllocatePileInventoryRequest.class
        )).isInstanceOf(JsonProcessingException.class);
    }
}
