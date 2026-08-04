package com.bebefish.erp.masterdata.infrastructure;

import com.bebefish.erp.masterdata.domain.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_category")
public class CategoryJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_code", nullable = false, length = 50)
    private String categoryCode;

    @Column(name = "category_name", nullable = false, length = 100)
    private String categoryName;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "level_no", nullable = false)
    private int level;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 500)
    private String remark;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected CategoryJpaEntity() {
    }

    CategoryJpaEntity(Category category) {
        apply(category);
    }

    void apply(Category category) {
        categoryCode = category.code();
        categoryName = category.name();
        parentId = category.parentId();
        level = category.level();
        sortOrder = category.sortOrder();
        status = category.status();
        remark = category.remark();
    }

    Category toDomain() {
        return new Category(
                id, categoryCode, categoryName, parentId, level, sortOrder, status, remark
        );
    }

    @PrePersist
    void prePersist() {
        var now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
