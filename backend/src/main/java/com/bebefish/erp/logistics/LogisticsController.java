package com.bebefish.erp.logistics;

import static com.bebefish.erp.logistics.LogisticsDtos.*;
import com.bebefish.erp.common.api.ApiResponse;
import com.bebefish.erp.common.api.PageResponse;
import com.bebefish.erp.common.security.ErpPrincipal;
import com.bebefish.erp.logistics.pricing.ProvinceCatalog;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/logistics")
public class LogisticsController {
    private final LogisticsCatalogService catalog; private final FreightQuoteService quotes;
    public LogisticsController(LogisticsCatalogService catalog,FreightQuoteService quotes) { this.catalog=catalog; this.quotes=quotes; }
    @GetMapping("/companies") @PreAuthorize("hasAuthority('logistics:view')")
    public ApiResponse<PageResponse<Company>> companies(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) String keyword,@RequestParam(required=false) String status) { return ApiResponse.success(catalog.companies(page,size,keyword,status)); }
    @GetMapping("/companies/{id}") @PreAuthorize("hasAuthority('logistics:view')")
    public ApiResponse<Company> company(@PathVariable long id) { return ApiResponse.success(catalog.company(id)); }
    @PostMapping("/companies") @PreAuthorize("hasAuthority('logistics:edit')")
    public ApiResponse<Company> create(@RequestBody CompanyInput in,@AuthenticationPrincipal ErpPrincipal actor) { return ApiResponse.success(catalog.createCompany(in,actor)); }
    @PutMapping("/companies/{id}") @PreAuthorize("hasAuthority('logistics:edit')")
    public ApiResponse<Company> update(@PathVariable long id,@RequestBody CompanyInput in,@AuthenticationPrincipal ErpPrincipal actor) { return ApiResponse.success(catalog.updateCompany(id,in,actor)); }
    @PostMapping("/companies/{id}/status") @PreAuthorize("hasAuthority('logistics:edit')")
    public ApiResponse<Company> status(@PathVariable long id,@RequestBody StatusInput in,@AuthenticationPrincipal ErpPrincipal actor) { return ApiResponse.success(catalog.companyStatus(id,in,actor)); }
    @GetMapping("/companies/{id}/account") @PreAuthorize("hasAuthority('logistics:configure')")
    public ApiResponse<AccountView> account(@PathVariable long id,@AuthenticationPrincipal ErpPrincipal actor) { return ApiResponse.success(catalog.account(id,actor)); }
    @PutMapping("/companies/{id}/account") @PreAuthorize("hasAuthority('logistics:configure')")
    public ApiResponse<AccountView> account(@PathVariable long id,@RequestBody AccountInput in,@AuthenticationPrincipal ErpPrincipal actor) { return ApiResponse.success(catalog.saveAccount(id,in,actor)); }
    @GetMapping("/companies/{id}/accounts") @PreAuthorize("hasAuthority('logistics:configure')")
    public ApiResponse<PageResponse<AccountView>> accounts(@PathVariable long id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@AuthenticationPrincipal ErpPrincipal actor) {
        LogisticsCatalogService.page(page,size); var all=catalog.accounts(id,actor); return ApiResponse.success(new PageResponse<>(all.stream().skip((long)(page-1)*size).limit(size).toList(),page,size,all.size()));
    }
    @GetMapping("/companies/{id}/templates") @PreAuthorize("hasAuthority('logistics:view')")
    public ApiResponse<PageResponse<Template>> templates(@PathVariable long id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@AuthenticationPrincipal ErpPrincipal actor) {
        LogisticsCatalogService.page(page,size); var all=catalog.templates(id,actor); return ApiResponse.success(new PageResponse<>(all.stream().skip((long)(page-1)*size).limit(size).toList(),page,size,all.size()));
    }
    @PostMapping("/companies/{id}/templates") @PreAuthorize("hasAuthority('logistics:template')")
    public ApiResponse<Template> createTemplate(@PathVariable long id,@RequestBody TemplateInput in,@AuthenticationPrincipal ErpPrincipal actor) { return ApiResponse.success(catalog.createTemplate(id,in,actor)); }
    @GetMapping("/templates/{id}") @PreAuthorize("hasAuthority('logistics:view')")
    public ApiResponse<Template> template(@PathVariable long id,@AuthenticationPrincipal ErpPrincipal actor) { return ApiResponse.success(catalog.template(id,actor)); }
    @PutMapping("/templates/{id}") @PreAuthorize("hasAuthority('logistics:template')")
    public ApiResponse<Template> updateTemplate(@PathVariable long id,@RequestBody TemplateInput in,@AuthenticationPrincipal ErpPrincipal actor) { return ApiResponse.success(catalog.updateTemplate(id,in,actor)); }
    @PostMapping("/templates/{id}/status") @PreAuthorize("hasAuthority('logistics:template')")
    public ApiResponse<Template> templateStatus(@PathVariable long id,@RequestBody StatusInput in,@AuthenticationPrincipal ErpPrincipal actor) { return ApiResponse.success(catalog.templateStatus(id,in,actor)); }
    @GetMapping("/provinces") @PreAuthorize("hasAnyAuthority('logistics:view','logistics:template','logistics:configure','shipping:view')")
    public ApiResponse<List<ProvinceCatalog.Province>> provinces() { return ApiResponse.success(ProvinceCatalog.options()); }
    @PostMapping("/quotes") @PreAuthorize("hasAuthority('shipping:view')")
    public ApiResponse<QuoteBatch> quote(@RequestBody QuoteInput in,@AuthenticationPrincipal ErpPrincipal principal) { return ApiResponse.success(quotes.quote(in,principal.employeeId())); }
}
