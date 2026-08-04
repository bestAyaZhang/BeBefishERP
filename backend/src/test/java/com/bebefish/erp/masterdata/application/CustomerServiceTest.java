package com.bebefish.erp.masterdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.masterdata.domain.Customer;
import com.bebefish.erp.masterdata.domain.CustomerRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class CustomerServiceTest {
    private FakeCustomerRepository repository;
    private CustomerService service;

    @BeforeEach
    void setUp() {
        repository = new FakeCustomerRepository();
        service = new CustomerService(repository);
    }

    @Test
    void createsCustomerWithDefaultDeliveryPreferences() {
        var customer = service.create(command("C0001", "杭州万象家居", "delivery", "monthly"));

        assertThat(customer.transportMethod()).isEqualTo("delivery");
        assertThat(customer.settlementCycle()).isEqualTo("monthly");
        assertThat(customer.status()).isEqualTo("enabled");
    }

    @Test
    void generatesCustomerNumberWhenCreateDoesNotProvideOne() {
        var customer = service.create(command(null, "自动编号客户", "delivery", "monthly"));

        assertThat(customer.number()).matches("CUS-[0-9]{8}-[A-Z0-9]{6}");
    }

    @Test
    void keepsExistingCustomerNumberWhenUpdateDoesNotProvideOne() {
        var existing = repository.save(customer(null, "C0001", "客户甲", false));

        var updated = service.update(existing.id(), command(null, "更新后的客户", "delivery", "monthly"));

        assertThat(updated.number()).isEqualTo("C0001");
        assertThat(updated.name()).isEqualTo("更新后的客户");
    }

    @Test
    void requiresContactMobileAndAddressWhenCreatingCustomer() {
        var command = new SaveCustomerCommand(
                null, "缺少资料客户", null, null, null,
                "浙江省", "杭州市", "余杭区", "良渚街道88号",
                "delivery", "monthly", null
        );

        assertThatThrownBy(() -> service.create(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("联系人不能为空");
    }

    @Test
    void rejectsUnknownDeliveryPreferences() {
        assertThatThrownBy(() -> service.create(command("C0001", "客户", "air", "weekly")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("运输方式无效");
    }

    @Test
    void customerNumberMustBeUnique() {
        repository.save(customer(null, "C0001", "客户甲", false));

        assertThatThrownBy(() -> service.create(command("C0001", "客户乙", "pickup", "daily")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.code()).isEqualTo("DUPLICATE_CUSTOMER_NO"));
    }

    @Test
    void walkInCustomerCannotBeDisabled() {
        var walkIn = repository.save(customer(null, "WALK_IN", "散客", true));

        assertThatThrownBy(() -> service.changeStatus(walkIn.id(), "disabled"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("散客不能停用");
    }

    private SaveCustomerCommand command(
            String number,
            String name,
            String transportMethod,
            String settlementCycle
    ) {
        return new SaveCustomerCommand(
                number, name, "张经理", "13800138000", "0571-88888888",
                "浙江省", "杭州市", "滨江区", "江南大道88号",
                transportMethod, settlementCycle, null
        );
    }

    private Customer customer(Long id, String number, String name, boolean system) {
        return new Customer(
                id, number, name, null, null, null, null, null, null, null,
                "pickup", "daily", system, "enabled", null
        );
    }

    private static final class FakeCustomerRepository implements CustomerRepository {
        private final AtomicLong sequence = new AtomicLong();
        private final Map<Long, Customer> values = new LinkedHashMap<>();

        @Override
        public boolean existsByNumber(String number, Long excludedId) {
            return values.values().stream().anyMatch(value -> value.number().equals(number)
                    && !value.id().equals(excludedId));
        }

        @Override
        public Customer save(Customer customer) {
            var id = customer.id() == null ? sequence.incrementAndGet() : customer.id();
            var saved = new Customer(
                    id, customer.number(), customer.name(), customer.contactPerson(), customer.mobile(),
                    customer.telephone(), customer.province(), customer.city(), customer.district(),
                    customer.detailAddress(), customer.transportMethod(), customer.settlementCycle(),
                    customer.system(), customer.status(), customer.remark()
            );
            values.put(id, saved);
            return saved;
        }

        @Override
        public Optional<Customer> findById(long id) {
            return Optional.ofNullable(values.get(id));
        }

        @Override
        public Page<Customer> findAll(String keyword, String status, Pageable pageable) {
            return new PageImpl<>(values.values().stream().toList(), pageable, values.size());
        }
    }
}
