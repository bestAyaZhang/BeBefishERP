package com.bebefish.erp.masterdata.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SpringDataCustomerRepository extends JpaRepository<CustomerJpaEntity, Long>, JpaSpecificationExecutor<CustomerJpaEntity> {
    boolean existsByCustomerNo(String number);
    boolean existsByCustomerNoAndIdNot(String number, Long id);
}
