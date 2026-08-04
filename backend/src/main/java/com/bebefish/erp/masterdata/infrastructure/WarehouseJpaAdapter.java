package com.bebefish.erp.masterdata.infrastructure;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.masterdata.domain.Warehouse;
import com.bebefish.erp.masterdata.domain.WarehouseRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

@Repository
public class WarehouseJpaAdapter implements WarehouseRepository {
    private final SpringDataWarehouseRepository repository;

    public WarehouseJpaAdapter(SpringDataWarehouseRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsByNumber(String number, Long excludedId) {
        return excludedId == null
                ? repository.existsByWarehouseNo(number)
                : repository.existsByWarehouseNoAndIdNot(number, excludedId);
    }

    @Override
    public boolean existsByName(String name, Long excludedId) {
        return excludedId == null
                ? repository.existsByWarehouseName(name)
                : repository.existsByWarehouseNameAndIdNot(name, excludedId);
    }

    @Override
    public Warehouse save(Warehouse warehouse) {
        WarehouseJpaEntity entity;
        if (warehouse.id() == null) {
            entity = new WarehouseJpaEntity(warehouse);
        } else {
            entity = repository.findById(warehouse.id()).orElseThrow(this::notFound);
            entity.apply(warehouse);
        }
        return repository.save(entity).toDomain();
    }

    @Override
    public Optional<Warehouse> findById(long id) {
        return repository.findById(id).map(WarehouseJpaEntity::toDomain);
    }

    @Override
    public Optional<Warehouse> findDefault() {
        return repository.findFirstByDefaultWarehouseTrueAndStatus("enabled")
                .map(WarehouseJpaEntity::toDomain);
    }

    @Override
    public void clearDefault() {
        repository.clearDefault();
    }

    @Override
    public Page<Warehouse> findAll(String keyword, String status, Pageable pageable) {
        Specification<WarehouseJpaEntity> specification = (root, query, criteriaBuilder) -> {
            var predicates = new ArrayList<Predicate>();
            if (keyword != null) {
                var pattern = "%" + keyword + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(root.get("warehouseNo"), pattern),
                        criteriaBuilder.like(root.get("warehouseName"), pattern)
                ));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
        return repository.findAll(specification, pageable).map(WarehouseJpaEntity::toDomain);
    }

    private BusinessException notFound() {
        return new BusinessException("WAREHOUSE_NOT_FOUND", HttpStatus.NOT_FOUND, "仓库不存在");
    }
}
