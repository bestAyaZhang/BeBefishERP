package com.bebefish.erp.platform.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlatformSchemaIntegrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test void createsPlatformCatalogWithNullableShipmentReferences() {
        assertThat(jdbc.queryForList("select table_name from information_schema.tables where table_schema=database()", String.class))
            .contains("sales_platform", "platform_shop");
        assertThat(jdbc.queryForList("select column_name from information_schema.columns where table_schema=database() and table_name='shipment' and is_nullable='YES'", String.class))
            .contains("platform_id", "shop_id");
        assertThat(jdbc.queryForList("select code from sys_permission where module_key='platform' order by code", String.class))
            .containsExactly("platform:create", "platform:edit", "platform:view");
    }

    @Test void rejectsInvalidStatusDuplicateNamesAndCrossPlatformShop() {
        long p1 = platform("TEST_P1", "测试平台一");
        long p2 = platform("TEST_P2", "测试平台二");
        shop(p1,"TEST_S1","同名店");
        shop(p2,"TEST_S2","同名店");
        assertThatThrownBy(() -> shop(p1,"TEST_S3","同名店")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("update sales_platform set status='unknown' where id=?",p1)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("update sales_platform set sort_order=-1 where id=?",p1)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    long platform(String code,String name) {
        jdbc.update("insert into sales_platform(platform_code,platform_name,status,sort_order,remark,version_no,created_by,updated_by,created_at,updated_at) values (?,?,'enabled',0,'',0,'test','test',now(3),now(3))",code,name);
        return jdbc.queryForObject("select id from sales_platform where platform_code=?",Long.class,code);
    }
    void shop(long p,String code,String name) {
        jdbc.update("insert into platform_shop(platform_id,shop_code,shop_name,status,sort_order,remark,version_no,created_by,updated_by,created_at,updated_at) values (?,?,?,'enabled',0,'',0,'test','test',now(3),now(3))",p,code,name);
    }
}
