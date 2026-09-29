package com.bebefish.erp.platform.api;
import com.bebefish.erp.common.api.*;
import com.bebefish.erp.common.security.ErpPrincipal;
import com.bebefish.erp.platform.application.PlatformCatalogService;
import com.bebefish.erp.platform.domain.PlatformShop;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/platform-shops")
public class PlatformShopController {
    private final PlatformCatalogService service;
    public PlatformShopController(PlatformCatalogService service) { this.service=service; }
    @GetMapping @PreAuthorize("hasAuthority('platform:view')")
    public ApiResponse<PageResponse<PlatformShop>> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) String keyword,@RequestParam(required=false) String status,@RequestParam(required=false) Long platformId) {
        return ApiResponse.success(PageResponse.from(service.listShops(keyword,status,platformId,page,size)));
    }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('platform:view')")
    public ApiResponse<PlatformShop> get(@PathVariable long id) { return ApiResponse.success(service.getShop(id)); }
    @PostMapping @PreAuthorize("hasAuthority('platform:view') and hasAuthority('platform:create')")
    public ApiResponse<PlatformShop> create(@Valid @RequestBody PlatformDtos.Create input,@AuthenticationPrincipal ErpPrincipal p) { return ApiResponse.success(service.createShop(input,p.operatorIdentifier())); }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('platform:view') and hasAuthority('platform:edit')")
    public ApiResponse<PlatformShop> update(@PathVariable long id,@Valid @RequestBody PlatformDtos.Update input,@AuthenticationPrincipal ErpPrincipal p) { return ApiResponse.success(service.updateShop(id,input,p.operatorIdentifier())); }
    @PostMapping("/{id}/status") @PreAuthorize("hasAuthority('platform:view') and hasAuthority('platform:edit')")
    public ApiResponse<PlatformShop> status(@PathVariable long id,@Valid @RequestBody PlatformDtos.Status input,@AuthenticationPrincipal ErpPrincipal p) { return ApiResponse.success(service.changeShopStatus(id,input.status(),input.version(),p.operatorIdentifier())); }
}
