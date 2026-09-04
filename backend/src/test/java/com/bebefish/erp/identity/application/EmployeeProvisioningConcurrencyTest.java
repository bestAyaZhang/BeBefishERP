package com.bebefish.erp.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.bebefish.erp.feishu.FeishuEmployeeProfile;
import com.bebefish.erp.feishu.FeishuOAuthIdentity;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "erp.feishu.allowed-tenant-key=tenant-a")
class EmployeeProvisioningConcurrencyTest {
    private static final String OPEN_ID = "open-concurrent";
    private static final String UNION_ID = "union-concurrent";

    @Autowired
    private EmployeeProvisioningService service;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void cleanFixture() {
        var userIds = jdbc.queryForList(
                "select user_id from sys_feishu_identity where tenant_key = 'tenant-a' and open_id = ?",
                Long.class,
                OPEN_ID
        );
        for (Long userId : userIds) {
            Long employeeId = jdbc.queryForObject("select employee_id from sys_user where id = ?", Long.class, userId);
            jdbc.update("delete from sys_feishu_identity where user_id = ?", userId);
            jdbc.update("delete from sys_user_role where user_id = ?", userId);
            jdbc.update("delete from sys_user where id = ?", userId);
            jdbc.update("delete from employee where id = ?", employeeId);
        }
    }

    @Test
    void tenConcurrentFirstLoginsCreateOneEmployeeAndAccount() {
        var identity = new FeishuOAuthIdentity(
                "tenant-a", OPEN_ID, UNION_ID, "并发员工", null, null
        );
        var profile = new FeishuEmployeeProfile(OPEN_ID, null, null, null, "并发员工");

        try (var executor = Executors.newFixedThreadPool(10)) {
            var futures = IntStream.range(0, 10)
                    .mapToObj(index -> CompletableFuture.supplyAsync(
                            () -> service.provision(identity, profile), executor
                    ))
                    .toList();
            var userIds = futures.stream().map(CompletableFuture::join)
                    .map(result -> result.account().id())
                    .distinct()
                    .toList();

            assertThat(userIds).hasSize(1);
            assertThat(jdbc.queryForObject(
                    "select count(*) from sys_feishu_identity where tenant_key = 'tenant-a' and open_id = ?",
                    Integer.class,
                    OPEN_ID
            )).isOne();
        }
    }
}
