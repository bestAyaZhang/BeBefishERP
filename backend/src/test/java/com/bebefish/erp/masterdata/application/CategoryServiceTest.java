package com.bebefish.erp.masterdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.masterdata.domain.Category;
import com.bebefish.erp.masterdata.domain.CategoryRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class CategoryServiceTest {
    private InMemoryCategoryRepository repository;
    private CategoryService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryCategoryRepository();
        service = new CategoryService(repository);
    }

    @Test
    void createsOnlyLevelOneEnabledCategoryInVersionOne() {
        var result = service.create(new SaveCategoryCommand("GLASS", "玻璃杯", 10, "杯具"));

        assertThat(result.parentId()).isNull();
        assertThat(result.level()).isEqualTo(1);
        assertThat(result.status()).isEqualTo("enabled");
    }

    @Test
    void generatesCategoryCodeWhenCreateDoesNotProvideOne() {
        var result = service.create(new SaveCategoryCommand(null, "自动编号分类", 10, null));

        assertThat(result.code()).matches("CAT-[0-9]{8}-[A-Z0-9]{6}");
    }

    @Test
    void keepsExistingCategoryCodeWhenUpdateDoesNotProvideOne() {
        var existing = repository.save(category(null, "GLASS", "玻璃杯"));

        var updated = service.update(existing.id(), new SaveCategoryCommand(null, "更新后的分类", 20, null));

        assertThat(updated.code()).isEqualTo("GLASS");
        assertThat(updated.name()).isEqualTo("更新后的分类");
    }

    @Test
    void rejectsDuplicateCategoryCode() {
        repository.save(category(null, "GLASS", "玻璃杯"));

        assertThatThrownBy(() -> service.create(
                new SaveCategoryCommand("GLASS", "啤酒杯", 20, null)
        )).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.code()).isEqualTo("DUPLICATE_CATEGORY_CODE");
            assertThat(exception.getMessage()).isEqualTo("分类编码已存在");
        });
    }

    @Test
    void rejectsDuplicateCategoryName() {
        repository.save(category(null, "GLASS", "玻璃杯"));

        assertThatThrownBy(() -> service.create(
                new SaveCategoryCommand("BEER", "玻璃杯", 20, null)
        )).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.code()).isEqualTo("DUPLICATE_CATEGORY_NAME");
            assertThat(exception.getMessage()).isEqualTo("分类名称已存在");
        });
    }

    @Test
    void updateExcludesCurrentCategoryFromDuplicateChecks() {
        var existing = repository.save(category(null, "GLASS", "玻璃杯"));

        var updated = service.update(existing.id(), new SaveCategoryCommand(
                "GLASS", "玻璃杯", 30, "更新备注"
        ));

        assertThat(updated.sortOrder()).isEqualTo(30);
        assertThat(updated.remark()).isEqualTo("更新备注");
    }

    @Test
    void updateRejectsAnotherCategoryName() {
        var first = repository.save(category(null, "GLASS", "玻璃杯"));
        repository.save(category(null, "BEER", "啤酒杯"));

        assertThatThrownBy(() -> service.update(first.id(), new SaveCategoryCommand(
                "GLASS", "啤酒杯", 10, null
        ))).isInstanceOf(BusinessException.class)
                .hasMessage("分类名称已存在");
    }

    @Test
    void changesCategoryStatus() {
        var existing = repository.save(category(null, "GLASS", "玻璃杯"));

        var disabled = service.changeStatus(existing.id(), "disabled");

        assertThat(disabled.status()).isEqualTo("disabled");
    }

    @Test
    void rejectsUnknownCategory() {
        assertThatThrownBy(() -> service.changeStatus(999L, "disabled"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("CATEGORY_NOT_FOUND");
                    assertThat(exception.status().value()).isEqualTo(404);
                });
    }

    private Category category(Long id, String code, String name) {
        return new Category(id, code, name, null, 1, 10, "enabled", null);
    }

    private static final class InMemoryCategoryRepository implements CategoryRepository {
        private final AtomicLong sequence = new AtomicLong();
        private final Map<Long, Category> categories = new LinkedHashMap<>();

        @Override
        public boolean existsByCode(String code, Long excludedId) {
            return categories.values().stream()
                    .anyMatch(category -> category.code().equals(code)
                            && !category.id().equals(excludedId));
        }

        @Override
        public boolean existsByName(String name, Long excludedId) {
            return categories.values().stream()
                    .anyMatch(category -> category.name().equals(name)
                            && !category.id().equals(excludedId));
        }

        @Override
        public Category save(Category category) {
            var id = category.id() == null ? sequence.incrementAndGet() : category.id();
            var saved = new Category(
                    id, category.code(), category.name(), category.parentId(), category.level(),
                    category.sortOrder(), category.status(), category.remark()
            );
            categories.put(id, saved);
            return saved;
        }

        @Override
        public Optional<Category> findById(long id) {
            return Optional.ofNullable(categories.get(id));
        }

        @Override
        public Page<Category> findAll(String keyword, String status, Pageable pageable) {
            return new PageImpl<>(categories.values().stream().toList(), pageable, categories.size());
        }
    }
}
