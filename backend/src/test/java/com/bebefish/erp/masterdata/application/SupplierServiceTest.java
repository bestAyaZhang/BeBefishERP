package com.bebefish.erp.masterdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.masterdata.domain.Supplier;
import com.bebefish.erp.masterdata.domain.SupplierRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class SupplierServiceTest {
    private FakeSupplierRepository repository;
    private SupplierService service;

    @BeforeEach
    void setUp() {
        repository = new FakeSupplierRepository();
        service = new SupplierService(repository);
    }

    @Test
    void createsEnabledSupplier() {
        var supplier = service.create(command("S0001", "义乌玻璃厂"));

        assertThat(supplier.status()).isEqualTo("enabled");
        assertThat(supplier.name()).isEqualTo("义乌玻璃厂");
    }

    @Test
    void generatesSupplierNumberWhenCreateDoesNotProvideOne() {
        var supplier = service.create(command(null, "自动编号供应商"));

        assertThat(supplier.number()).matches("SUP-[0-9]{8}-[A-Z0-9]{6}");
    }

    @Test
    void keepsExistingSupplierNumberWhenUpdateDoesNotProvideOne() {
        var existing = repository.save(supplier(null, "S0001", "义乌玻璃厂"));

        var updated = service.update(existing.id(), command(null, "更新后的供应商"));

        assertThat(updated.number()).isEqualTo("S0001");
        assertThat(updated.name()).isEqualTo("更新后的供应商");
    }

    @Test
    void supplierNumberMustBeUnique() {
        repository.save(supplier(null, "S0001", "义乌玻璃厂"));

        assertThatThrownBy(() -> service.create(command("S0001", "宁波玻璃厂")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("DUPLICATE_SUPPLIER_NO");
                    assertThat(exception.getMessage()).isEqualTo("供应商编号已存在");
                });
    }

    @Test
    void disablesSupplier() {
        var supplier = repository.save(supplier(null, "S0001", "义乌玻璃厂"));

        assertThat(service.changeStatus(supplier.id(), "disabled").status()).isEqualTo("disabled");
    }

    private SaveSupplierCommand command(String number, String name) {
        return new SaveSupplierCommand(
                number, name, "李经理", "13900139000", "0579-88888888", "义乌市", null
        );
    }

    private Supplier supplier(Long id, String number, String name) {
        return new Supplier(id, number, name, null, null, null, null, "enabled", null);
    }

    private static final class FakeSupplierRepository implements SupplierRepository {
        private final AtomicLong sequence = new AtomicLong();
        private final Map<Long, Supplier> values = new LinkedHashMap<>();

        @Override
        public boolean existsByNumber(String number, Long excludedId) {
            return values.values().stream().anyMatch(value -> value.number().equals(number)
                    && !value.id().equals(excludedId));
        }

        @Override
        public Supplier save(Supplier supplier) {
            var id = supplier.id() == null ? sequence.incrementAndGet() : supplier.id();
            var saved = new Supplier(
                    id, supplier.number(), supplier.name(), supplier.contactPerson(), supplier.mobile(),
                    supplier.telephone(), supplier.address(), supplier.status(), supplier.remark()
            );
            values.put(id, saved);
            return saved;
        }

        @Override
        public Optional<Supplier> findById(long id) {
            return Optional.ofNullable(values.get(id));
        }

        @Override
        public Page<Supplier> findAll(String keyword, String status, Pageable pageable) {
            return new PageImpl<>(values.values().stream().toList(), pageable, values.size());
        }
    }
}
