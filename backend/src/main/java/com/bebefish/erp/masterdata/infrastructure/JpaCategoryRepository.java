package com.bebefish.erp.masterdata.infrastructure;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.masterdata.domain.Category;
import com.bebefish.erp.masterdata.domain.CategoryRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

@Repository
public class JpaCategoryRepository implements CategoryRepository {
    private final SpringDataCategoryRepository repository;

    public JpaCategoryRepository(SpringDataCategoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsByCode(String code, Long excludedId) {
        return excludedId == null
                ? repository.existsByCategoryCode(code)
                : repository.existsByCategoryCodeAndIdNot(code, excludedId);
    }

    @Override
    public boolean existsByName(String name, Long excludedId) {
        return excludedId == null
                ? repository.existsByCategoryName(name)
                : repository.existsByCategoryNameAndIdNot(name, excludedId);
    }

    @Override
    public Category save(Category category) {
        CategoryJpaEntity entity;
        if (category.id() == null) {
            entity = new CategoryJpaEntity(category);
        } else {
            entity = repository.findById(category.id()).orElseThrow(() -> new BusinessException(
                    "CATEGORY_NOT_FOUND", HttpStatus.NOT_FOUND, "分类不存在"
            ));
            entity.apply(category);
        }
        return repository.save(entity).toDomain();
    }

    @Override
    public Optional<Category> findById(long id) {
        return repository.findById(id).map(CategoryJpaEntity::toDomain);
    }

    @Override
    public Page<Category> findAll(String keyword, String status, Pageable pageable) {
        Specification<CategoryJpaEntity> specification = (root, query, criteriaBuilder) -> {
            var predicates = new ArrayList<Predicate>();
            if (keyword != null) {
                var pattern = "%" + keyword + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(root.get("categoryCode"), pattern),
                        criteriaBuilder.like(root.get("categoryName"), pattern)
                ));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
        return repository.findAll(specification, pageable).map(CategoryJpaEntity::toDomain);
    }
}
