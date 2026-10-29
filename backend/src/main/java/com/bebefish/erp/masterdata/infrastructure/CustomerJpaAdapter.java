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
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    public CustomerJpaAdapter(SpringDataCustomerRepository repository, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.repository = repository; this.jdbc = jdbc;
    }
    public Optional<String> findPlatformStatus(long id) {
        return jdbc.query("select status from sales_platform where id = ?", (rs, n) -> rs.getString(1), id).stream().findFirst();
    }
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
            if (keyword != null) { var p = "%" + keyword + "%"; predicates.add(cb.or(cb.like(root.get("customerNo"), p), cb.like(root.get("customerName"), p), cb.like(root.get("mobile"), p), cb.like(root.get("telephone"), p), cb.like(root.get("contactPerson"), p))); }
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return repository.findAll(spec, pageable).map(CustomerJpaEntity::toDomain);
    }
    private BusinessException notFound() { return new BusinessException("CUSTOMER_NOT_FOUND", HttpStatus.NOT_FOUND, "客户不存在"); }
}
