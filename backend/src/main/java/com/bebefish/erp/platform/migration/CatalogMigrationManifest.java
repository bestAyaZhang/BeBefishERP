package com.bebefish.erp.platform.migration;
import java.util.List;
public record CatalogMigrationManifest(int schemaVersion,String batchId,String targetDatabase,long baselineMaxShipmentId,
        List<PlatformSeed> platforms,List<ShopSeed> shops,List<Mapping> mappings,List<ShipmentBaseline> shipmentBaselines) {
    public record PlatformSeed(String code,String name,String status,int sortOrder) {}
    public record ShopSeed(String code,String name,String platformCode,String status,int sortOrder) {}
    public record Mapping(String rawPlatform,String rawShopName,String platformCode,String shopCode) {}
    public record ShipmentBaseline(long id,long version,String rawPlatform,String rawShopName,Long platformId,Long shopId) {}
}
