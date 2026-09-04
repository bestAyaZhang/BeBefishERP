package com.bebefish.erp.authorization.application;

import com.bebefish.erp.authorization.domain.FeishuRoleMappingRepository;
import com.bebefish.erp.feishu.FeishuBusinessRole;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeishuRoleSyncService {
    public static final String DEGRADED_WARNING = "FEISHU_ROLE_SYNC_DEGRADED";

    private final FeishuRoleMappingRepository mappings;

    public FeishuRoleSyncService(FeishuRoleMappingRepository mappings) {
        this.mappings = mappings;
    }

    @Transactional
    public List<String> sync(long userId, String tenantKey, List<FeishuBusinessRole> feishuRoles) {
        Set<String> feishuRoleIds = feishuRoles.stream()
                .map(FeishuBusinessRole::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<Long> roleIds = new LinkedHashSet<>(mappings.findEnabledErpRoleIds(tenantKey, feishuRoleIds));
        roleIds.add(mappings.basicEmployeeRoleId());
        mappings.replaceFeishuAssignments(userId, roleIds);
        return List.of();
    }

    @Transactional
    public List<String> syncDegraded(long userId) {
        mappings.replaceFeishuAssignments(userId, Set.of(mappings.basicEmployeeRoleId()));
        return List.of(DEGRADED_WARNING);
    }
}
