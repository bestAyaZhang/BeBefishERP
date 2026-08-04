package com.bebefish.erp.masterdata.infrastructure;

import com.bebefish.erp.masterdata.domain.Customer;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer")
public class CustomerJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "customer_no", nullable = false, length = 50) private String customerNo;
    @Column(name = "customer_name", nullable = false, length = 100) private String customerName;
    @Column(name = "contact_person", length = 100) private String contactPerson;
    @Column(length = 30) private String mobile;
    @Column(length = 30) private String telephone;
    @Column(length = 100) private String province;
    @Column(length = 100) private String city;
    @Column(length = 100) private String district;
    @Column(name = "detail_address", length = 500) private String detailAddress;
    @Column(name = "default_shipping_method", length = 100) private String transportMethod;
    @Column(name = "default_settlement_period", length = 100) private String settlementCycle;
    @Column(name = "is_system", nullable = false) private boolean system;
    @Column(nullable = false, length = 20) private String status;
    @Column(length = 500) private String remark;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    protected CustomerJpaEntity() {}
    CustomerJpaEntity(Customer value) { apply(value); }
    void apply(Customer v) {
        customerNo = v.number(); customerName = v.name(); contactPerson = v.contactPerson(); mobile = v.mobile();
        telephone = v.telephone(); province = v.province(); city = v.city(); district = v.district();
        detailAddress = v.detailAddress(); transportMethod = v.transportMethod(); settlementCycle = v.settlementCycle();
        system = v.system(); status = v.status(); remark = v.remark();
    }
    Customer toDomain() {
        return new Customer(id, customerNo, customerName, contactPerson, mobile, telephone, province, city,
                district, detailAddress, transportMethod, settlementCycle, system, status, remark);
    }
    @PrePersist void prePersist() { var now = LocalDateTime.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void preUpdate() { updatedAt = LocalDateTime.now(); }
}
