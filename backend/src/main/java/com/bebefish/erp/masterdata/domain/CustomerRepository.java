package com.bebefish.erp.masterdata.domain;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CustomerRepository {
    boolean existsByNumber(String number, Long excludedId);
    Customer save(Customer customer);
    Optional<Customer> findById(long id);
    Page<Customer> findAll(String keyword, String status, Pageable pageable);
}
