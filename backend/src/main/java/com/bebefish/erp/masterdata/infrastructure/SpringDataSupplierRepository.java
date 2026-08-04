package com.bebefish.erp.masterdata.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SpringDataSupplierRepository extends JpaRepository<SupplierJpaEntity, Long>, JpaSpecificationExecutor<SupplierJpaEntity> {
    boolean existsBySupplierNo(String number);
    boolean existsBySupplierNoAndIdNot(String number, Long id);
}
