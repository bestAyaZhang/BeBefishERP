package com.bebefish.erp.auth.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "sys_user")
public class UserAccountJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private EmployeeAccountJpaEntity employee;

    @Column(name = "mobile", length = 30)
    private String mobile;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "last_login_method", length = 20)
    private String lastLoginMethod;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserAccountJpaEntity() {
    }

    UserAccountJpaEntity(EmployeeAccountJpaEntity employee, String mobile, String passwordHash, String status, Instant now) {
        this.employee = employee;
        this.mobile = mobile;
        this.passwordHash = passwordHash;
        this.status = status;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void update(String mobile, String passwordHash, String status, Instant now) {
        this.mobile = mobile;
        this.passwordHash = passwordHash;
        this.status = status;
        this.updatedAt = now;
    }

    void recordLogin(String loginMethod, Instant now) {
        this.lastLoginMethod = loginMethod;
        this.lastLoginAt = now;
        this.updatedAt = now;
    }

    Long id() {
        return id;
    }

    EmployeeAccountJpaEntity employee() {
        return employee;
    }

    String mobile() {
        return mobile;
    }

    String passwordHash() {
        return passwordHash;
    }

    String status() {
        return status;
    }
}

@Entity
@Table(name = "employee")
class EmployeeAccountJpaEntity {
    @Id
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "employment_type", nullable = false, length = 20)
    private String employmentType;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "avatar_url", length = 1000)
    private String avatarUrl;

    protected EmployeeAccountJpaEntity() {
    }

    Long id() {
        return id;
    }

    String name() {
        return name;
    }

    String employmentType() {
        return employmentType;
    }

    String status() {
        return status;
    }

    String avatarUrl() {
        return avatarUrl;
    }
}
