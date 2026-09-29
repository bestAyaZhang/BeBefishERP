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
    private final com.bebefish.erp.platform.application.ShippingSourceResolver sources;

    public ShipmentService(ShipmentRepository repository, Validator validator, Clock clock, ShipmentEditPolicy editPolicy,
                           com.bebefish.erp.platform.application.ShippingSourceResolver sources) {
        this.repository = repository;
        this.validator = validator;
        this.clock = clock;
        this.editPolicy = editPolicy;
        this.sources = sources;
    }

    @Transactional
    public Shipment create(ShipmentFormInput input, String auditOperator, String displayOperator) {
        var form = validate(input);
        form = form.withSource(sources.resolveForCreate(input.platformId(), input.shopId()));
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
        var previous = repository.findByIdForUpdate(id).orElseThrow(() ->
                new BusinessException("SHIPMENT_NOT_FOUND", HttpStatus.NOT_FOUND, "发货单不存在"));
        if (version < 0 || previous.version() != version) throw conflict();
        Long platformId=input.platformId(), shopId=input.shopId();
        if(platformId==null && shopId==null) {
            if(!java.util.Objects.equals(input.platform(),previous.content().platform())
                || !java.util.Objects.equals(input.shopName(),previous.content().shopName()))
                throw new BusinessException("SHIPMENT_SOURCE_SELECTION_REQUIRED",HttpStatus.BAD_REQUEST,"请刷新页面并选择平台和店铺");
            platformId=previous.content().platformId(); shopId=previous.content().shopId();
        }
        form=form.withSource(sources.resolveForUpdate(previous.content().form().source(),platformId,shopId));
        editPolicy.assertAllowed(previous.content(), form, repository.hasNonRejectedLogisticsOrder(id));
        var content = ShipmentContent.from(previous.content().shipmentDate(), form, status, previous.content().orderer(),
                previous.content().logisticsCompany(), previous.content().trackingNo());
        var next = new Shipment(id, previous.shipmentNo(), content, version + 1, previous.createdBy(), auditOperator,
                previous.createdAt(), LocalDateTime.now(clock), previous.logisticsOrderState());
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
        if(query.platformId()!=null && query.platform()!=null && !query.platform().isBlank()) throw invalid("平台ID与历史平台名称不能同时筛选");
        if(query.unlinkedOnly() && query.shopId()!=null) throw invalid("历史未关联筛选不能指定店铺");
        sources.validateFilter(query.platformId(),query.shopId());
        return repository.findAll(query, PageRequest.of(page - 1, size));
    }

    @Transactional(readOnly = true)
    public ShipmentSummary summary(LocalDate date) {
        return repository.summary(date == null ? LocalDate.now(clock) : date);
    }

    private ShipmentFormInput validate(ShipmentFormInput input) {
        if (input == null) throw invalid("请填写发货资料");
        var normalized = input.normalized();
        var violations = validator.validate(normalized);
        if (!violations.isEmpty()) throw invalid(violations.iterator().next().getMessage());
        return normalized;
    }

    private void validateStatus(String status) {
        if (status == null || !STATUSES.contains(status)) throw invalid("备货状态无效");
    }

    private static String clean(String value) { return value == null ? "" : value.strip(); }
    private BusinessException invalid(String message) { return new BusinessException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, message); }
    private BusinessException conflict() { return new BusinessException("SHIPMENT_VERSION_CONFLICT", HttpStatus.CONFLICT,
            "发货单已更新，或已有物流订单且关键资料不能直接修改。请重新打开核对；当前填写内容已保留"); }
}
