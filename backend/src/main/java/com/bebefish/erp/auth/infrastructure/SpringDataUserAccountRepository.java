package com.bebefish.erp.auth.infrastructure;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataUserAccountRepository extends JpaRepository<UserAccountJpaEntity, Long> {
    Optional<UserAccountJpaEntity> findByMobile(String mobile);

    Optional<UserAccountJpaEntity> findByEmployee_Id(Long employeeId);
}
