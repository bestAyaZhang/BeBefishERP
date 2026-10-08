package com.bebefish.erp.shipping.api;

import com.bebefish.erp.shipping.domain.ShipmentFormInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record SaveShipmentRequest(
        @NotNull @Valid ShipmentFormInput form,
        @Pattern(regexp = "unfinished|completed|out_of_stock|partially_shipped", message = "发货状态无效") String status,
        @Min(0) Long version,
        @Size(max=20) List<@NotNull @Positive Long> preparerEmployeeIds
) {
    public SaveShipmentRequest(ShipmentFormInput form, String status, Long version) { this(form, status, version, null); }
}
