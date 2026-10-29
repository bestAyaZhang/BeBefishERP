package com.bebefish.erp.product.api;

import com.bebefish.erp.common.api.ApiResponse;
import com.bebefish.erp.common.api.PageResponse;
import com.bebefish.erp.common.security.ErpPrincipal;
import com.bebefish.erp.product.application.PddCostPlanService;
import com.bebefish.erp.product.application.PddCostPlanService.*;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/product-cost-plans")
public class PddCostPlanController {
    private final PddCostPlanService service;
    public PddCostPlanController(PddCostPlanService service) { this.service=service; }
    @GetMapping
    @PreAuthorize("hasAuthority('product:view')")
    public ApiResponse<PageResponse<Summary>> list(@RequestParam(defaultValue="") String keyword,
            @RequestParam(defaultValue="false") boolean archived,@RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="20") int size) { return ApiResponse.success(service.list(keyword,archived,page,size)); }
    @GetMapping("/sku-options")
    @PreAuthorize("hasAuthority('product:view')")
    public ApiResponse<List<SkuOption>> options(@RequestParam(defaultValue="") String keyword) { return ApiResponse.success(service.options(keyword)); }
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('product:view')")
    public ApiResponse<Plan> get(@PathVariable long id) { return ApiResponse.success(service.get(id)); }
    @PostMapping
    @PreAuthorize("hasAuthority('product:edit')")
    public ApiResponse<Plan> create(@RequestBody SavePlan request,@AuthenticationPrincipal ErpPrincipal principal) {
        return ApiResponse.success(service.save(null,request,principal.operatorIdentifier()));
    }
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('product:edit')")
    public ApiResponse<Plan> update(@PathVariable long id,@RequestBody SavePlan request,@AuthenticationPrincipal ErpPrincipal principal) {
        return ApiResponse.success(service.save(id,request,principal.operatorIdentifier()));
    }
    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('product:edit')")
    public ApiResponse<Void> archive(@PathVariable long id,@RequestParam long version,@AuthenticationPrincipal ErpPrincipal principal) {
        service.archive(id,version,true,principal.operatorIdentifier()); return ApiResponse.success(null);
    }
    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('product:edit')")
    public ApiResponse<Void> restore(@PathVariable long id,@RequestParam long version,@AuthenticationPrincipal ErpPrincipal principal) {
        service.archive(id,version,false,principal.operatorIdentifier()); return ApiResponse.success(null);
    }
}
