package com.bebefish.erp.platform.infrastructure;
import com.bebefish.erp.platform.application.*;
import com.bebefish.erp.platform.api.PlatformDtos;
import com.bebefish.erp.common.api.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test")
class PlatformCatalogConcurrencyTest {
    @Autowired PlatformCatalogService catalog; @Autowired ShippingSourceResolver sources;
    @Autowired PlatformTransactionManager manager; @Autowired JdbcTemplate jdbc;
    @Test void lockingReadSeesDisableCommittedAfterEarlierSnapshot() throws Exception {
        String code="CC_"+System.nanoTime();
        var p=catalog.createPlatform(new PlatformDtos.Create(code,code,0,"",null),"test");
        var s=catalog.createShop(new PlatformDtos.Create(code+"_S",code,0,"",p.id(),"ecommerce","运营",null),"test");
        try (var pool=Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(manager).executeWithoutResult(tx -> {
                assertThat(catalog.getPlatform(p.id()).status()).isEqualTo("enabled"); // establish repeatable-read snapshot
                try { pool.submit(()->catalog.changePlatformStatus(p.id(),"disabled",0,"other")).get(5,TimeUnit.SECONDS); }
                catch(Exception e) { throw new RuntimeException(e); }
                assertThatThrownBy(()->sources.resolveForCreate(p.id(),s.id())).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.code()).isEqualTo("PLATFORM_DISABLED"));
            });
        } finally { jdbc.update("delete from platform_shop where id=?",s.id()); jdbc.update("delete from sales_platform where id=?",p.id()); }
    }
    @Test void twoEditorsCannotOverwriteTheSameVersion() throws Exception {
        String code="CAS_"+System.nanoTime();
        var p=catalog.createPlatform(new PlatformDtos.Create(code,code,0,"",null),"test");
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            Callable<Boolean> edit=()-> { start.await(); try { catalog.updatePlatform(p.id(),new PlatformDtos.Update(code,1,"",0L),"test"); return true; } catch(BusinessException e) { assertThat(e.code()).isEqualTo("PLATFORM_VERSION_CONFLICT"); return false; } };
            var a=pool.submit(edit); var b=pool.submit(edit); start.countDown();
            assertThat((a.get(5,TimeUnit.SECONDS)?1:0)+(b.get(5,TimeUnit.SECONDS)?1:0)).isEqualTo(1);
        } finally { jdbc.update("delete from sales_platform where id=?",p.id()); }
    }
}
