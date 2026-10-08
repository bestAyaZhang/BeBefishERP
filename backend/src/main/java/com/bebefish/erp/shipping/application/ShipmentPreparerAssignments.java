package com.bebefish.erp.shipping.application;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.shipping.infrastructure.JdbcShippingFormOptionsRepository;
import java.util.List;
import java.util.HashSet;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ShipmentPreparerAssignments {
    private final JdbcShippingFormOptionsRepository employees;
    public ShipmentPreparerAssignments(JdbcShippingFormOptionsRepository employees) { this.employees = employees; }

    public List<Long> validate(List<Long> ids, List<String> names) {
        if (ids == null || ids.isEmpty() || ids.size() > 20 || ids.stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(ids).size() != ids.size()) throw invalid();
        var options = employees.findActivePreparers();
        var selected = options.stream().filter(e -> ids.contains(e.employeeId())).toList();
        if (selected.size() != ids.size() || !new HashSet<>(names).equals(new HashSet<>(selected.stream().map(e -> e.employeeName().strip()).toList())))
            throw invalid();
        return List.copyOf(ids);
    }
    private BusinessException invalid() {
        return new BusinessException("SHIPMENT_PREPARER_SELECTION_INVALID", HttpStatus.BAD_REQUEST, "请重新选择在职备货人员，核对姓名与员工账号");
    }
}
