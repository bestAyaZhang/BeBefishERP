package com.bebefish.erp.masterdata.infrastructure;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.masterdata.domain.Supplier;
import com.bebefish.erp.masterdata.domain.SupplierRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

@Repository
public class SupplierJpaAdapter implements SupplierRepository {
    private final SpringDataSupplierRepository repository;
    public SupplierJpaAdapter(SpringDataSupplierRepository repository) { this.repository = repository; }
    public boolean existsByNumber(String number, Long excludedId) {
        return excludedId == null ? repository.existsBySupplierNo(number) : repository.existsBySupplierNoAndIdNot(number, excludedId);
    }
    public Supplier save(Supplier value) {
        SupplierJpaEntity entity;
        if (value.id() == null) entity = new SupplierJpaEntity(value);
        else { entity = repository.findById(value.id()).orElseThrow(() -> notFound()); entity.apply(value); }
        return repository.save(entity).toDomain();
    }
    public Optional<Supplier> findById(long id) { return repository.findById(id).map(SupplierJpaEntity::toDomain); }
    public Page<Supplier> findAll(String keyword, String status, Pageable pageable) {
        Specification<SupplierJpaEntity> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (keyword != null) { var p = "%" + keyword + "%"; predicates.add(cb.or(cb.like(root.get("supplierNo"), p), cb.like(root.get("supplierName"), p))); }
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return repository.findAll(spec, pageable).map(SupplierJpaEntity::toDomain);
    }
    private BusinessException notFound() { return new BusinessException("SUPPLIER_NOT_FOUND", HttpStatus.NOT_FOUND, "供应商不存在"); }
}
