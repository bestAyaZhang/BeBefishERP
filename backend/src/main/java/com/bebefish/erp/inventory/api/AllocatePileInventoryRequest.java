package com.bebefish.erp.inventory.api;

import com.bebefish.erp.inventory.application.WarehousePileAllocationService;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.io.IOException;

public record AllocatePileInventoryRequest(
        @NotBlank String palletId,
        @NotNull @Positive @JsonDeserialize(using = WholeNumberDeserializer.class) Long skuId,
        @NotNull @Positive @JsonDeserialize(using = WholeNumberDeserializer.class) Long units
) {
    WarehousePileAllocationService.Command toCommand() {
        return new WarehousePileAllocationService.Command(palletId.trim(), skuId, units);
    }

    public static final class WholeNumberDeserializer extends JsonDeserializer<Long> {
        @Override
        public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            return parser.currentToken() == JsonToken.VALUE_NUMBER_INT ? parser.getLongValue() : 0L;
        }
    }
}
