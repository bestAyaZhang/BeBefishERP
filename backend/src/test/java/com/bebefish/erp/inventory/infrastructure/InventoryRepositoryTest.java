package com.bebefish.erp.inventory.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bebefish.erp.inventory.domain.InventoryBalance;
import com.bebefish.erp.inventory.domain.InventoryRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaInventoryRepository.class)
class InventoryRepositoryTest {
    @Autowired
    private InventoryRepository repository;

    @Test
    void createsOneBalancePerWarehouseAndSku() {
        repository.saveBalance(new InventoryBalance(null, 1L, 10L, new BigDecimal("12.0000"), 0L));

        assertThatThrownBy(() -> repository.saveBalance(
                new InventoryBalance(null, 1L, 10L, BigDecimal.ONE, 0L)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void locksBalancesInSkuOrder() {
        repository.saveBalance(new InventoryBalance(null, 1L, 30L, new BigDecimal("3.0000"), 0L));
        repository.saveBalance(new InventoryBalance(null, 1L, 10L, new BigDecimal("1.0000"), 0L));
        repository.saveBalance(new InventoryBalance(null, 1L, 20L, new BigDecimal("2.0000"), 0L));

        var balances = repository.lockBalances(1L, List.of(10L, 20L, 30L));

        assertThat(balances).extracting(InventoryBalance::skuId).containsExactly(10L, 20L, 30L);
    }
}
