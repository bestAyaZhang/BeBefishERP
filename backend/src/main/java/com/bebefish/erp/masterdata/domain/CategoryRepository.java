package com.bebefish.erp.masterdata.domain;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CategoryRepository {
    boolean existsByCode(String code, Long excludedId);

    boolean existsByName(String name, Long excludedId);

    Category save(Category category);

    Optional<Category> findById(long id);

    Page<Category> findAll(String keyword, String status, Pageable pageable);
}
