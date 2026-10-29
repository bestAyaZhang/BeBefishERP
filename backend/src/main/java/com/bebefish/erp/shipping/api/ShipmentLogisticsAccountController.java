package com.bebefish.erp.shipping.api;

import com.bebefish.erp.common.api.ApiResponse;
import com.bebefish.erp.common.security.ErpPrincipal;
import com.bebefish.erp.shipping.application.ShipmentService;
import com.bebefish.erp.shipping.logistics.ManagedLogisticsRouting;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/shipments")
public class ShipmentLogisticsAccountController {
    private final ShipmentService shipments;
    private final ManagedLogisticsRouting routing;
    public ShipmentLogisticsAccountController(ShipmentService shipments,ManagedLogisticsRouting routing){this.shipments=shipments;this.routing=routing;}
    public record Confirm(@NotNull @Positive Long accountId,@NotNull @PositiveOrZero Long version,boolean confirmed){}
    @GetMapping("/{id}/logistics-account") @PreAuthorize("hasAuthority('shipping:view')")
    public ApiResponse<ManagedLogisticsRouting.Binding> binding(@PathVariable long id){shipments.get(id);return ApiResponse.success(routing.binding(id));}
    @PostMapping("/{id}/logistics-account") @PreAuthorize("hasAuthority('shipping:view') and hasAuthority('logistics:configure')")
    public ApiResponse<ManagedLogisticsRouting.Binding> confirm(@PathVariable long id,@Valid @RequestBody Confirm input,@AuthenticationPrincipal ErpPrincipal actor){
        return ApiResponse.success(routing.confirmAccount(id,input.accountId(),input.version(),input.confirmed(),actor));
    }
}
