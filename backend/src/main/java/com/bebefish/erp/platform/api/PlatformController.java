package com.bebefish.erp.platform.api;
import com.bebefish.erp.common.api.*;
import com.bebefish.erp.common.security.ErpPrincipal;
import com.bebefish.erp.platform.application.PlatformCatalogService;
import com.bebefish.erp.platform.domain.Platform;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/platforms")
public class PlatformController {
    private final PlatformCatalogService service;
    public PlatformController(PlatformCatalogService service) { this.service=service; }
    @GetMapping @PreAuthorize("hasAuthority('platform:view')")
    public ApiResponse<PageResponse<Platform>> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) String keyword,@RequestParam(required=false) String status) {
        return ApiResponse.success(PageResponse.from(service.listPlatforms(keyword,status,page,size)));
    }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('platform:view')")
    public ApiResponse<Platform> get(@PathVariable long id) { return ApiResponse.success(service.getPlatform(id)); }
    @PostMapping @PreAuthorize("hasAuthority('platform:view') and hasAuthority('platform:create')")
    public ApiResponse<Platform> create(@Valid @RequestBody PlatformDtos.Create input,@AuthenticationPrincipal ErpPrincipal p) { return ApiResponse.success(service.createPlatform(input,p.operatorIdentifier())); }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('platform:view') and hasAuthority('platform:edit')")
    public ApiResponse<Platform> update(@PathVariable long id,@Valid @RequestBody PlatformDtos.Update input,@AuthenticationPrincipal ErpPrincipal p) { return ApiResponse.success(service.updatePlatform(id,input,p.operatorIdentifier())); }
    @PostMapping("/{id}/status") @PreAuthorize("hasAuthority('platform:view') and hasAuthority('platform:edit')")
    public ApiResponse<Platform> status(@PathVariable long id,@Valid @RequestBody PlatformDtos.Status input,@AuthenticationPrincipal ErpPrincipal p) { return ApiResponse.success(service.changePlatformStatus(id,input.status(),input.version(),p.operatorIdentifier())); }
}
