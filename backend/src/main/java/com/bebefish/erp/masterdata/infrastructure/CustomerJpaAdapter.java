package com.bebefish.erp.masterdata.infrastructure;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.masterdata.domain.Customer;
import com.bebefish.erp.masterdata.domain.CustomerRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerJpaAdapter implements CustomerRepository {
    private final SpringDataCustomerRepository repository;
    public CustomerJpaAdapter(SpringDataCustomerRepository repository) { this.repository = repository; }
    public boolean existsByNumber(String number, Long excludedId) {
        return excludedId == null ? repository.existsByCustomerNo(number) : repository.existsByCustomerNoAndIdNot(number, excludedId);
    }
    public Customer save(Customer value) {
        CustomerJpaEntity entity;
        if (value.id() == null) entity = new CustomerJpaEntity(value);
        else { entity = repository.findById(value.id()).orElseThrow(() -> notFound()); entity.apply(value); }
        return repository.save(entity).toDomain();
    }
    public Optional<Customer> findById(long id) { return repository.findById(id).map(CustomerJpaEntity::toDomain); }
    public Page<Customer> findAll(String keyword, String status, Pageable pageable) {
        Specification<CustomerJpaEntity> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (keyword != null) { var p = "%" + keyword + "%"; predicates.add(cb.or(cb.like(root.get("customerNo"), p), cb.like(root.get("customerName"), p))); }
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return repository.findAll(spec, pageable).map(CustomerJpaEntity::toDomain);
    }
    private BusinessException notFound() { return new BusinessException("CUSTOMER_NOT_FOUND", HttpStatus.NOT_FOUND, "客户不存在"); }
}
