package com.bebefish.erp.shipping.api;
import java.util.List;
public record ShippingFormOptions(List<String> shopNames,List<PreparerOption> preparers,List<PlatformOption> platforms,List<ShopOption> shops) {
    public ShippingFormOptions(List<String> shopNames,List<PreparerOption> preparers) { this(shopNames,preparers,List.of(),List.of()); }
    public record PreparerOption(long employeeId,String employeeName) {}
    public record PlatformOption(long id,String name) {}
    public record ShopOption(long id,long platformId,String name,String optionLabel) {}
}
