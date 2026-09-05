package com.bebefish.erp.authorization.application;

import com.bebefish.erp.authorization.api.PermissionDtos;
import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.feishu.FeishuBusinessRole;
import com.bebefish.erp.feishu.FeishuClientException;
import com.bebefish.erp.feishu.FeishuDirectoryClient;
import com.bebefish.erp.feishu.FeishuProperties;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeishuRoleMappingService {
    private final JdbcTemplate jdbc;
    private final FeishuDirectoryClient directory;
    private final FeishuProperties properties;
    private final FeishuRoleMappingSyncStatusWriter syncStatusWriter;

    public FeishuRoleMappingService(
            JdbcTemplate jdbc,
            FeishuDirectoryClient directory,
            FeishuProperties properties,
            FeishuRoleMappingSyncStatusWriter syncStatusWriter
    ) {
        this.jdbc = jdbc;
        this.directory = directory;
        this.properties = properties;
        this.syncStatusWriter = syncStatusWriter;
    }

    public List<FeishuBusinessRole> listFeishuRoles() {
        try {
            return directory.allBusinessRoles();
        } catch (FeishuClientException exception) {
            throw new BusinessException(
                    "FEISHU_ROLE_DIRECTORY_UNAVAILABLE",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "暂时无法读取飞书业务角色"
            );
        }
    }

    public List<PermissionDtos.MappingCandidateRole> candidateRoles() {
        return jdbc.query("""
                select id, code, name from sys_role
                where status = 'enabled' and is_sensitive = false
                order by name
                """, (rs, rowNum) -> new PermissionDtos.MappingCandidateRole(
                rs.getLong("id"), rs.getString("code"), rs.getString("name")
        ));
    }

    public List<PermissionDtos.FeishuRoleMappingResponse> listMappings() {
        return jdbc.query("""
                select m.*, r.name erp_role_name
                from sys_feishu_role_mapping m join sys_role r on r.id = m.erp_role_id
                where m.tenant_key = ? order by m.id
                """, (rs, rowNum) -> new PermissionDtos.FeishuRoleMappingResponse(
                rs.getString("feishu_role_id"), rs.getString("feishu_role_name"),
                rs.getLong("erp_role_id"), rs.getString("erp_role_name"), rs.getBoolean("enabled"),
                rs.getInt("member_count"), iso(rs.getTimestamp("last_synced_at")), rs.getString("last_error")
        ), properties.getAllowedTenantKey());
    }

    @Transactional
    public PermissionDtos.FeishuRoleMappingResponse save(
            String feishuRoleId,
            PermissionDtos.SaveFeishuRoleMappingRequest request
    ) {
        var role = jdbc.query("""
                select id, name, status, is_sensitive from sys_role where id = ?
                """, (rs, rowNum) -> new TargetRole(
                rs.getLong("id"), rs.getString("name"), rs.getString("status"),
                rs.getBoolean("is_sensitive")
        ), request.erpRoleId()).stream().findFirst().orElseThrow(() -> new BusinessException(
                "ROLE_NOT_FOUND", HttpStatus.NOT_FOUND, "角色不存在"
        ));
        if (role.sensitive() || !"enabled".equals(role.status())) {
            throw new BusinessException(
                    "SENSITIVE_ROLE_MAPPING_FORBIDDEN",
                    HttpStatus.BAD_REQUEST,
                    "敏感或停用角色不能作为飞书映射目标"
            );
        }
        jdbc.update("""
                insert into sys_feishu_role_mapping
                    (tenant_key, feishu_role_id, feishu_role_name, erp_role_id, enabled,
                     member_count, created_at, updated_at)
                values (?, ?, ?, ?, ?, 0, now(3), now(3))
                on duplicate key update feishu_role_name = values(feishu_role_name),
                    erp_role_id = values(erp_role_id), enabled = values(enabled), updated_at = now(3)
                """, properties.getAllowedTenantKey(), feishuRoleId, request.feishuRoleName().trim(),
                request.erpRoleId(), request.enabled());
        return listMappings().stream()
                .filter(mapping -> mapping.feishuRoleId().equals(feishuRoleId))
                .findFirst().orElseThrow();
    }

    @Transactional
    public void delete(String feishuRoleId) {
        jdbc.update("""
                delete from sys_feishu_role_mapping where tenant_key = ? and feishu_role_id = ?
                """, properties.getAllowedTenantKey(), feishuRoleId);
    }

    public List<PermissionDtos.FeishuRoleMappingResponse> sync() {
        try {
            var roles = listFeishuRoles();
            syncStatusWriter.markSuccessful(properties.getAllowedTenantKey(), roles, Instant.now());
            return listMappings();
        } catch (BusinessException exception) {
            syncStatusWriter.markFailed(
                    properties.getAllowedTenantKey(), "FEISHU_ROLE_DIRECTORY_UNAVAILABLE"
            );
            throw exception;
        }
    }

    private String iso(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().toString();
    }

    private record TargetRole(long id, String name, String status, boolean sensitive) {
    }
}
