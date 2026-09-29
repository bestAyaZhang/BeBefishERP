package com.bebefish.erp.platform.migration;
import com.bebefish.erp.common.config.DatabaseEnvironmentSafetyInitializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.*;
import java.util.*;

/** Explicit standalone command: never starts the ERP application, Flyway, or bootstrap runners. */
public final class CatalogMigrationApplication {
    public static void main(String[] args) throws Exception {
        var flags=new HashMap<String,String>();
        for(String arg:args) {
            if(!arg.startsWith("--") || !arg.contains("=")) throw new IllegalArgumentException("参数必须为 --name=value");
            var pair=arg.substring(2).split("=",2);
            if(!Set.of("mode","manifest","output").contains(pair[0]) || flags.put(pair[0],pair[1])!=null) throw new IllegalArgumentException("未知或重复参数");
        }
        String mode=flags.get("mode");
        if(!Set.of("dry-run","apply").contains(mode==null?"":mode) || !flags.containsKey("output")) throw new IllegalArgumentException("必须显式指定 --mode=dry-run|apply 和 --output=绝对路径");
        String profile=required("SPRING_PROFILES_ACTIVE");
        if(!Set.of("local","test","prod").contains(profile)) throw new IllegalArgumentException("必须指定单一 local/test/prod 数据库环境");
        String prefix="prod".equals(profile)?"ERP_PROD_DB_":"test".equals(profile)?"ERP_TEST_DB_":"ERP_DB_";
        String url=required(prefix+"URL");
        try(var context=new GenericApplicationContext()) {
            context.getEnvironment().setActiveProfiles(profile);
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("migration",Map.of("spring.datasource.url",url)));
            new DatabaseEnvironmentSafetyInitializer().initialize(context);
        }
        var ds=new DriverManagerDataSource(url,required(prefix+"USERNAME"),required(prefix+"PASSWORD"));
        var mapper=new ObjectMapper().findAndRegisterModules();
        var service=new CatalogMigrationService(new JdbcTemplate(ds),new DataSourceTransactionManager(ds),mapper);
        Path output=Path.of(flags.get("output"));
        if(!output.isAbsolute()) throw new IllegalArgumentException("输出路径必须是绝对路径");
        if("dry-run".equals(mode)) {
            var preview=service.dryRun(Arrays.asList(System.getenv().getOrDefault("ERP_SHIPPING_SHOP_NAMES","").split(",")));
            Files.createDirectory(output);
            mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("manifest.json").toFile(),preview.manifest());
            mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("preview.json").toFile(),preview);
            System.out.println("预演完成：共 "+preview.shipmentCount()+" 条，未关联 "+preview.unlinkedCount()+" 条。未写入数据库。");
        } else {
            if(!flags.containsKey("manifest")) throw new IllegalArgumentException("apply 必须指定 --manifest");
            Path manifest=Path.of(flags.get("manifest"));
            if(!manifest.isAbsolute()) throw new IllegalArgumentException("清单路径必须是绝对路径");
            var result=service.apply(mapper.readValue(manifest.toFile(),CatalogMigrationManifest.class),output);
            System.out.println("关联："+result.updated()+"；跳过："+result.skipped()+"；冲突："+result.conflicts());
            if(result.conflicts()>0) System.exit(2);
        }
    }
    private static String required(String key) {
        String value=System.getenv(key);
        if(value==null || value.isBlank()) throw new IllegalArgumentException("缺少环境变量："+key);
        return value;
    }
}
