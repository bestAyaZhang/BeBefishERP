package com.bebefish.erp.masterdata.infrastructure;

import com.bebefish.erp.masterdata.domain.Supplier;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "supplier")
public class SupplierJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "supplier_no", nullable = false, length = 50) private String supplierNo;
    @Column(name = "supplier_name", nullable = false, length = 100) private String supplierName;
    @Column(name = "contact_person", length = 100) private String contactPerson;
    @Column(length = 30) private String mobile;
    @Column(length = 30) private String telephone;
    @Column(length = 500) private String address;
    @Column(nullable = false, length = 20) private String status;
    @Column(length = 500) private String remark;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    protected SupplierJpaEntity() {}
    SupplierJpaEntity(Supplier value) { apply(value); }
    void apply(Supplier v) {
        supplierNo = v.number(); supplierName = v.name(); contactPerson = v.contactPerson(); mobile = v.mobile();
        telephone = v.telephone(); address = v.address(); status = v.status(); remark = v.remark();
    }
    Supplier toDomain() {
        return new Supplier(id, supplierNo, supplierName, contactPerson, mobile, telephone, address, status, remark);
    }
    @PrePersist void prePersist() { var now = LocalDateTime.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void preUpdate() { updatedAt = LocalDateTime.now(); }
}
