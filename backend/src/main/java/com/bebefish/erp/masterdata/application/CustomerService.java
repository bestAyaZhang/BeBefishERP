package com.bebefish.erp.masterdata.application;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.masterdata.domain.Customer;
import com.bebefish.erp.masterdata.domain.CustomerRepository;
import java.util.Set;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {
    private static final Set<String> TRANSPORT_METHODS = Set.of("pickup", "delivery", "freight", "express");
    private static final Set<String> SETTLEMENT_CYCLES = Set.of("daily", "monthly", "quarterly", "yearly");
    private static final Set<String> STATUSES = Set.of("enabled", "disabled");

    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Customer create(SaveCustomerCommand command) {
        var value = normalize(command);
        var number = value.number() == null ? generateCustomerNumber() : value.number();
        var contactPerson = required(value.contactPerson(), "联系人不能为空");
        var mobile = required(value.mobile(), "手机号不能为空");
        var province = required(value.province(), "省不能为空");
        var city = required(value.city(), "市不能为空");
        var district = required(value.district(), "区/县不能为空");
        var detailAddress = required(value.detailAddress(), "详细地址不能为空");
        ensureUnique(number, null);
        return repository.save(new Customer(
                null, number, value.name(), contactPerson, mobile, value.telephone(),
                province, city, district, detailAddress,
                value.transportMethod(), value.settlementCycle(), false, "enabled", value.remark()
        ));
    }

    @Transactional
    public Customer update(long id, SaveCustomerCommand command) {
        var existing = get(id);
        var value = normalize(command);
        var number = value.number() == null ? existing.number() : value.number();
        ensureUnique(number, id);
        return repository.save(new Customer(
                id, number, value.name(), value.contactPerson(), value.mobile(), value.telephone(),
                value.province(), value.city(), value.district(), value.detailAddress(),
                value.transportMethod(), value.settlementCycle(), existing.system(), existing.status(), value.remark()
        ));
    }

    @Transactional
    public Customer changeStatus(long id, String status) {
        var existing = get(id);
        var normalized = normalizeStatus(status);
        if ("disabled".equals(normalized) && (existing.system() || "WALK_IN".equals(existing.number()))) {
            throw new BusinessException("WALK_IN_CUSTOMER_REQUIRED", HttpStatus.CONFLICT, "散客不能停用");
        }
        return repository.save(new Customer(
                existing.id(), existing.number(), existing.name(), existing.contactPerson(), existing.mobile(),
                existing.telephone(), existing.province(), existing.city(), existing.district(),
                existing.detailAddress(), existing.transportMethod(), existing.settlementCycle(),
                existing.system(), normalized, existing.remark()
        ));
    }

    @Transactional(readOnly = true)
    public Customer get(long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(
                "CUSTOMER_NOT_FOUND", HttpStatus.NOT_FOUND, "客户不存在"
        ));
    }

    @Transactional(readOnly = true)
    public Page<Customer> list(String keyword, String status, Pageable pageable) {
        return repository.findAll(optional(keyword), status == null || status.isBlank() ? null : normalizeStatus(status), pageable);
    }

    private SaveCustomerCommand normalize(SaveCustomerCommand command) {
        if (command == null) throw validation("客户信息不能为空");
        var transport = required(command.transportMethod(), "运输方式不能为空").toLowerCase();
        if (!TRANSPORT_METHODS.contains(transport)) throw validation("运输方式无效");
        var settlement = required(command.settlementCycle(), "结算周期不能为空").toLowerCase();
        if (!SETTLEMENT_CYCLES.contains(settlement)) throw validation("结算周期无效");
        return new SaveCustomerCommand(
                optional(command.number()), required(command.name(), "客户名称不能为空"),
                optional(command.contactPerson()), optional(command.mobile()), optional(command.telephone()),
                optional(command.province()), optional(command.city()), optional(command.district()),
                optional(command.detailAddress()), transport, settlement, optional(command.remark())
        );
    }

    private String generateCustomerNumber() {
        String candidate;
        do {
            candidate = "CUS-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-"
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        } while (repository.existsByNumber(candidate, null));
        return candidate;
    }

    private void ensureUnique(String number, Long excludedId) {
        if (repository.existsByNumber(number, excludedId)) {
            throw new BusinessException("DUPLICATE_CUSTOMER_NO", HttpStatus.CONFLICT, "客户编号已存在");
        }
    }

    private String normalizeStatus(String status) {
        var value = required(status, "客户状态不能为空").toLowerCase();
        if (!STATUSES.contains(value)) throw validation("客户状态无效");
        return value;
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) throw validation(message);
        return value.trim();
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BusinessException validation(String message) {
        return new BusinessException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, message);
    }
}
