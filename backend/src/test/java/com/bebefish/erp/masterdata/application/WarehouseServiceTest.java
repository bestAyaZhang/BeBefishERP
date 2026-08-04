package com.bebefish.erp.masterdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.masterdata.domain.Warehouse;
import com.bebefish.erp.masterdata.domain.WarehouseRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class WarehouseServiceTest {
    private FakeWarehouseRepository repository;
    private WarehouseService service;

    @BeforeEach
    void setUp() {
        repository = new FakeWarehouseRepository();
        service = new WarehouseService(repository);
    }

    @Test
    void settingNewDefaultClearsPreviousDefault() {
        var first = repository.save(warehouse(null, "WH01", true, "enabled"));
        var second = repository.save(warehouse(null, "WH02", false, "enabled"));

        service.setDefault(second.id());

        assertThat(repository.findById(first.id()).orElseThrow().isDefault()).isFalse();
        assertThat(repository.findById(second.id()).orElseThrow().isDefault()).isTrue();
        assertThat(service.getDefaultWarehouse().id()).isEqualTo(second.id());
    }

    @Test
    void creatingNewDefaultClearsPreviousDefault() {
        var first = repository.save(warehouse(null, "WH01", true, "enabled"));

        var second = service.create(new SaveWarehouseCommand("WH02", "二号仓", "杭州", true, null));

        assertThat(repository.findById(first.id()).orElseThrow().isDefault()).isFalse();
        assertThat(second.isDefault()).isTrue();
    }

    @Test
    void generatesWarehouseNumberWhenCreateDoesNotProvideOne() {
        var warehouse = service.create(new SaveWarehouseCommand(null, "自动编号仓库", "杭州", false, null));

        assertThat(warehouse.number()).matches("WH-[0-9]{8}-[A-Z0-9]{6}");
    }

    @Test
    void keepsExistingWarehouseNumberWhenUpdateDoesNotProvideOne() {
        var existing = repository.save(warehouse(null, "WH01", false, "enabled"));

        var updated = service.update(existing.id(), new SaveWarehouseCommand(null, "更新后的仓库", "杭州", false, null));

        assertThat(updated.number()).isEqualTo("WH01");
        assertThat(updated.name()).isEqualTo("更新后的仓库");
    }

    @Test
    void defaultWarehouseCannotBeDisabledBeforeAnotherIsSelected() {
        var warehouse = repository.save(warehouse(null, "WH01", true, "enabled"));

        assertThatThrownBy(() -> service.changeStatus(warehouse.id(), "disabled"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("DEFAULT_WAREHOUSE_REQUIRED");
                    assertThat(exception.getMessage()).isEqualTo("请先指定另一个默认仓库");
                });
    }

    @Test
    void disabledWarehouseCannotBecomeDefault() {
        var warehouse = repository.save(warehouse(null, "WH01", false, "disabled"));

        assertThatThrownBy(() -> service.setDefault(warehouse.id()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("禁用仓库不能设为默认仓库");
    }

    private Warehouse warehouse(Long id, String number, boolean isDefault, String status) {
        return new Warehouse(id, number, number, null, isDefault, status, null);
    }

    private static final class FakeWarehouseRepository implements WarehouseRepository {
        private final AtomicLong sequence = new AtomicLong();
        private final Map<Long, Warehouse> values = new LinkedHashMap<>();

        @Override
        public boolean existsByNumber(String number, Long excludedId) {
            return values.values().stream().anyMatch(value -> value.number().equals(number)
                    && !value.id().equals(excludedId));
        }

        @Override
        public boolean existsByName(String name, Long excludedId) {
            return values.values().stream().anyMatch(value -> value.name().equals(name)
                    && !value.id().equals(excludedId));
        }

        @Override
        public Warehouse save(Warehouse warehouse) {
            var id = warehouse.id() == null ? sequence.incrementAndGet() : warehouse.id();
            var saved = new Warehouse(id, warehouse.number(), warehouse.name(), warehouse.address(),
                    warehouse.isDefault(), warehouse.status(), warehouse.remark());
            values.put(id, saved);
            return saved;
        }

        @Override
        public Optional<Warehouse> findById(long id) {
            return Optional.ofNullable(values.get(id));
        }

        @Override
        public Optional<Warehouse> findDefault() {
            return values.values().stream().filter(value -> value.isDefault() && "enabled".equals(value.status())).findFirst();
        }

        @Override
        public void clearDefault() {
            values.replaceAll((id, value) -> new Warehouse(id, value.number(), value.name(), value.address(),
                    false, value.status(), value.remark()));
        }

        @Override
        public Page<Warehouse> findAll(String keyword, String status, Pageable pageable) {
            return new PageImpl<>(values.values().stream().toList(), pageable, values.size());
        }
    }
}
