package com.bebefish.erp.masterdata.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SpringDataCategoryRepository extends
        JpaRepository<CategoryJpaEntity, Long>,
        JpaSpecificationExecutor<CategoryJpaEntity> {
    boolean existsByCategoryCode(String categoryCode);

    boolean existsByCategoryCodeAndIdNot(String categoryCode, Long id);

    boolean existsByCategoryName(String categoryName);

    boolean existsByCategoryNameAndIdNot(String categoryName, Long id);
}
