package com.bebefish.erp.shipping.infrastructure;

import com.bebefish.erp.platform.api.PlatformDtos;
import com.bebefish.erp.platform.application.PlatformCatalogService;
import com.bebefish.erp.platform.domain.Platform;
import com.bebefish.erp.platform.domain.PlatformShop;
import com.bebefish.erp.shipping.application.ShipmentService;
import com.bebefish.erp.shipping.domain.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @Transactional
class YimiDraftPersistenceTest {
    @Autowired ShipmentService shipments;
    @Autowired PlatformCatalogService platforms;
    @Autowired JdbcTemplate jdbc;

    @Test void explicitYimiOptionsSurviveDatabaseCreateAndUpdate() {
        String code="YIMI-DRAFT-"+java.util.UUID.randomUUID();
        jdbc.update("insert into employee(employee_no,name,employment_type,status,source,created_at,updated_at) values(?,?,'formal','active','manual',now(3),now(3))",code,"壹米草稿备货员");
        long employee=jdbc.queryForObject("select id from employee where employee_no=?",Long.class,code);
        var platform=platforms.createPlatform(new PlatformDtos.Create(null,code,0,"",null),"test");
        var shop=platforms.createShop(new PlatformDtos.Create(null,"壹米草稿测试店",0,"",platform.id(),"ecommerce","验收",null),"test");
        var options=new YimiOrderOptions(0,3,"2");
        var form=form(platform,shop,options);
        var created=shipments.create(form,List.of(employee),"test","验收");
        assertThat(shipments.get(created.id()).content().orderDraft().yimi()).isEqualTo(options);
        var updatedOptions=new YimiOrderOptions(1,3,"3");
        var updated=shipments.update(created.id(),form(platform,shop,updatedOptions),"unfinished",created.version(),"test",List.of(employee),true);
        assertThat(shipments.get(updated.id()).content().orderDraft().yimi()).isEqualTo(updatedOptions);
    }
    private ShipmentFormInput form(Platform platform,PlatformShop shop,YimiOrderOptions options) {
        return new ShipmentFormInput(platform.name(),shop.name(),List.of("壹米草稿备货员"),"验收收件人","13800000003",
            "浙江省","杭州市","余杭区","验收路1号","商品1件","",null,
            new AneOrderDraft("商品","纸箱",BigDecimal.TEN,new BigDecimal("0.12"),1,524,179,104,"",options),
            platform.id(),shop.id(),"验收寄件人","13800000001","浙江省","杭州市","余杭区","验收仓库1号");
    }
}
