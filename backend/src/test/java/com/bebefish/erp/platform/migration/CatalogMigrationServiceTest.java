package com.bebefish.erp.platform.migration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @Transactional
class CatalogMigrationServiceTest {
    @Autowired JdbcTemplate jdbc; @Autowired PlatformTransactionManager transactions; @Autowired ObjectMapper mapper;
    @TempDir Path temp;
    CatalogMigrationService service() { return new CatalogMigrationService(jdbc,transactions,mapper); }
    @Test void dryRunNeverWritesOrGuessesPlatformForConfiguredShops() {
        int before=jdbc.queryForObject("select count(*) from sales_platform",Integer.class);
        var report=service().dryRun(List.of(" 配置店 ","配置店",""));
        assertThat(report.configuredShopNames()).containsExactly("配置店");
        assertThat(report.manifest().platforms()).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from sales_platform",Integer.class)).isEqualTo(before);
    }
    @Test void repeatedApplyIsIdempotentAndNeverUpdatesHistoricalSnapshots() throws Exception {
        jdbc.update("""
            insert into shipment(shipment_no,shipment_date,recipient_name,recipient_phone,recipient_address,platform,shop_name,
            preparation_content,remark,status,created_by,updated_by,created_at,updated_at,preparers_json)
            values('MIGRATION-TEST',current_date(),'','','','旧平台名','旧店名','旧备货','备注','partially_shipped','test','test',now(3),now(3),json_array())
            """);
        var preview=service().dryRun(List.of());
        var original=preview.manifest();
        var m=new CatalogMigrationManifest(1,"test-batch",original.targetDatabase(),original.baselineMaxShipmentId(),
            List.of(new CatalogMigrationManifest.PlatformSeed("MIGRATION_P","当前平台名","enabled",0)),
            List.of(new CatalogMigrationManifest.ShopSeed("MIGRATION_S","当前店名","MIGRATION_P","enabled",0)),
            List.of(new CatalogMigrationManifest.Mapping("旧平台名","旧店名","MIGRATION_P","MIGRATION_S")),original.shipmentBaselines());
        var result=service().apply(m,temp.resolve("first"));
        assertThat(result.updated()).isEqualTo(1);
        var row=jdbc.queryForMap("select platform,shop_name,status,remark,version_no,shop_id from shipment where shipment_no='MIGRATION-TEST'");
        assertThat(row).containsEntry("platform","旧平台名").containsEntry("shop_name","旧店名").containsEntry("status","partially_shipped").containsEntry("remark","备注");
        assertThat(row.get("shop_id")).isNotNull();
        assertThat(service().apply(m,temp.resolve("second")).updated()).isZero();
        assertThat(jdbc.queryForObject("select version_no from shipment where shipment_no='MIGRATION-TEST'",Long.class)).isEqualTo(1L);
    }
    @Test void invalidOrAmbiguousManifestCannotWrite() throws Exception {
        var p=service().dryRun(List.of()).manifest();
        var m=new CatalogMigrationManifest(1,"ambiguous",p.targetDatabase(),p.baselineMaxShipmentId(),
            List.of(new CatalogMigrationManifest.PlatformSeed("MIG_BAD","不应创建","enabled",0)),List.of(),
            List.of(new CatalogMigrationManifest.Mapping("旧","店","MIG_BAD",null),new CatalogMigrationManifest.Mapping("旧","店","MIG_BAD",null)),List.of());
        assertThatThrownBy(()->service().apply(m,temp.resolve("invalid"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("select count(*) from sales_platform where platform_code='MIG_BAD'",Integer.class)).isZero();
    }
    @Test void concurrentShipmentEditIsReportedAsConflict() throws Exception {
        jdbc.update("""
            insert into shipment(shipment_no,shipment_date,recipient_name,recipient_phone,recipient_address,platform,shop_name,
            preparation_content,remark,status,created_by,updated_by,created_at,updated_at,preparers_json)
            values('MIGRATION-CONFLICT',current_date(),'','','','原平台','原店','备货','','unfinished','test','test',now(3),now(3),json_array())
            """);
        var before=service().dryRun(List.of()).manifest();
        jdbc.update("update shipment set remark='并发修改',version_no=version_no+1 where shipment_no='MIGRATION-CONFLICT'");
        var m=new CatalogMigrationManifest(1,"conflict",before.targetDatabase(),before.baselineMaxShipmentId(),
            List.of(new CatalogMigrationManifest.PlatformSeed("CONFLICT_P","归属平台","enabled",0)),List.of(),
            List.of(new CatalogMigrationManifest.Mapping("原平台","原店","CONFLICT_P",null)),before.shipmentBaselines());
        assertThat(service().apply(m,temp.resolve("conflict")).conflicts()).isEqualTo(1);
        assertThat(jdbc.queryForMap("select platform_id,remark from shipment where shipment_no='MIGRATION-CONFLICT'"))
            .containsEntry("platform_id",null).containsEntry("remark","并发修改");
    }
}
