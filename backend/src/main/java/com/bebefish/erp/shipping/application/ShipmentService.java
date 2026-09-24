package com.bebefish.erp.shipping.application;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.shipping.domain.*;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShipmentService {
    private static final Set<String> STATUSES = Set.of("unfinished", "completed", "out_of_stock", "partially_shipped");
    private final ShipmentRepository repository;
    private final Validator validator;
    private final Clock clock;
    private final ShipmentEditPolicy editPolicy;

    public ShipmentService(ShipmentRepository repository, Validator validator, Clock clock, ShipmentEditPolicy editPolicy) {
        this.repository = repository;
        this.validator = validator;
        this.clock = clock;
        this.editPolicy = editPolicy;
    }

    @Transactional
    public Shipment create(ShipmentFormInput input, String auditOperator, String displayOperator) {
        var form = validate(input);
        var now = LocalDateTime.now(clock);
        var number = "FH" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-"
                + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        var content = ShipmentContent.from(LocalDate.now(clock), form, "unfinished", clean(displayOperator), "", "");
        return repository.insert(new Shipment(null, number, content, 0, auditOperator, auditOperator, now, now));
    }

    @Transactional
    public Shipment update(long id, ShipmentFormInput input, String status, long version, String auditOperator) {
        var form = validate(input);
        validateStatus(status);
        var previous = get(id);
        if (version < 0 || previous.version() != version) throw conflict();
        editPolicy.assertAllowed(previous.content(), form, repository.hasNonRejectedLogisticsOrder(id));
        var content = ShipmentContent.from(previous.content().shipmentDate(), form, status, previous.content().orderer(),
                previous.content().logisticsCompany(), previous.content().trackingNo());
        var next = new Shipment(id, previous.shipmentNo(), content, version + 1, previous.createdBy(), auditOperator,
                previous.createdAt(), LocalDateTime.now(clock));
        if (!repository.update(next, version)) throw conflict();
        return next;
    }

    @Transactional(readOnly = true)
    public Shipment get(long id) {
        return repository.findById(id).orElseThrow(() ->
                new BusinessException("SHIPMENT_NOT_FOUND", HttpStatus.NOT_FOUND, "发货单不存在"));
    }

    @Transactional(readOnly = true)
    public Page<Shipment> list(ShipmentQuery query, int page, int size) {
        if (page < 1 || size < 1 || size > 100) throw invalid("分页参数无效");
        if (query.status() != null && !query.status().isBlank()) validateStatus(query.status());
        if (query.dateFrom() != null && query.dateTo() != null && query.dateFrom().isAfter(query.dateTo()))
            throw invalid("开始日期不能晚于结束日期");
        if (query.keyword() != null && query.keyword().length() > 200) throw invalid("搜索关键词不能超过200字");
        if (query.platform() != null && query.platform().length() > 100) throw invalid("平台名称不能超过100字");
        return repository.findAll(query, PageRequest.of(page - 1, size));
    }

    private ShipmentFormInput validate(ShipmentFormInput input) {
        if (input == null) throw invalid("请填写发货资料");
        var normalized = input.normalized();
        var violations = validator.validate(normalized);
        if (!violations.isEmpty()) throw invalid(violations.iterator().next().getMessage());
        return normalized;
    }

    private void validateStatus(String status) {
        if (status == null || !STATUSES.contains(status)) throw invalid("发货状态无效");
    }

    private static String clean(String value) { return value == null ? "" : value.strip(); }
    private BusinessException invalid(String message) { return new BusinessException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, message); }
    private BusinessException conflict() { return new BusinessException("SHIPMENT_VERSION_CONFLICT", HttpStatus.CONFLICT,
            "发货单已更新，或已有物流订单且关键资料不能直接修改。请重新打开核对；当前填写内容已保留"); }
}
