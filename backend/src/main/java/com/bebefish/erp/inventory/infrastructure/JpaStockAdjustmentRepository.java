package com.bebefish.erp.inventory.infrastructure;

import com.bebefish.erp.inventory.domain.StockAdjustment;
import com.bebefish.erp.inventory.domain.StockAdjustmentRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaStockAdjustmentRepository implements StockAdjustmentRepository {
    private final SpringDataStockAdjustmentRepository repository;

    public JpaStockAdjustmentRepository(SpringDataStockAdjustmentRepository repository) {
        this.repository = repository;
    }

    @Override
    public StockAdjustment save(StockAdjustment adjustment) {
        StockAdjustmentJpaEntity entity;
        if (adjustment.id() == null) {
            entity = new StockAdjustmentJpaEntity(adjustment);
        } else {
            entity = repository.findById(adjustment.id()).orElseThrow();
            entity.apply(adjustment);
        }
        return repository.saveAndFlush(entity).toDomain();
    }

    @Override
    public Optional<StockAdjustment> findById(long id) {
        return repository.findById(id).map(StockAdjustmentJpaEntity::toDomain);
    }

    @Override
    public void deleteById(long id) {
        repository.deleteById(id);
        repository.flush();
    }
}
