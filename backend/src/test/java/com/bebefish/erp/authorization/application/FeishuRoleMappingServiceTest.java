package com.bebefish.erp.authorization.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.feishu.FeishuClientException;
import com.bebefish.erp.feishu.infrastructure.HttpFeishuClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "erp.feishu.allowed-tenant-key=tenant-a")
class FeishuRoleMappingServiceTest {
    private String roleId;

    @Autowired
    private FeishuRoleMappingService service;

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private HttpFeishuClient directory;

    @AfterEach
    void removeCommittedFailureFixture() {
        if (roleId != null) {
            jdbc.update("delete from sys_feishu_role_mapping where tenant_key = 'tenant-a' and feishu_role_id = ?", roleId);
        }
    }

    @Test
    void persistsSyncFailureAfterRemoteDirectoryError() {
        roleId = "sync-failure-" + System.nanoTime();
        long basicRoleId = jdbc.queryForObject(
                "select id from sys_role where code = 'BASIC_EMPLOYEE'", Long.class
        );
        jdbc.update("""
                insert into sys_feishu_role_mapping
                    (tenant_key, feishu_role_id, feishu_role_name, erp_role_id, enabled,
                     member_count, created_at, updated_at)
                values ('tenant-a', ?, '待同步角色', ?, true, 0, now(3), now(3))
                """, roleId, basicRoleId);
        when(directory.allBusinessRoles()).thenThrow(new FeishuClientException("remote unavailable"));

        assertThatThrownBy(service::sync).isInstanceOf(BusinessException.class);

        assertThat(jdbc.queryForObject("""
                select last_error from sys_feishu_role_mapping
                where tenant_key = 'tenant-a' and feishu_role_id = ?
                """, String.class, roleId)).isEqualTo("FEISHU_ROLE_DIRECTORY_UNAVAILABLE");
    }
}
