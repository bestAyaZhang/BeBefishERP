package com.bebefish.erp.auth.infrastructure;

import com.bebefish.erp.auth.domain.EmployeeStatus;
import com.bebefish.erp.auth.domain.EmploymentType;
import com.bebefish.erp.auth.domain.UserAccount;
import com.bebefish.erp.auth.domain.UserAccountRepository;
import com.bebefish.erp.auth.domain.UserStatus;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaUserAccountRepository implements UserAccountRepository {
    private final SpringDataUserAccountRepository users;
    private final EntityManager entityManager;
    private final Clock clock;

    public JpaUserAccountRepository(
            SpringDataUserAccountRepository users,
            EntityManager entityManager,
            Clock clock
    ) {
        this.users = users;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Override
    public Optional<UserAccount> findById(long id) {
        return users.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<UserAccount> findByMobile(String mobile) {
        return users.findByMobile(mobile).map(this::toDomain);
    }

    @Override
    public Optional<UserAccount> findByEmployeeId(long employeeId) {
        return users.findByEmployee_Id(employeeId).map(this::toDomain);
    }

    @Override
    @Transactional
    public UserAccount save(UserAccount account) {
        Instant now = Instant.now(clock);
        UserAccountJpaEntity entity;
        if (account.id() > 0) {
            entity = users.findById(account.id()).orElseThrow();
            entity.update(account.mobile(), account.passwordHash(), db(account.userStatus()), now);
        } else {
            var employee = entityManager.getReference(EmployeeAccountJpaEntity.class, account.employeeId());
            entity = new UserAccountJpaEntity(
                    employee,
                    account.mobile(),
                    account.passwordHash(),
                    db(account.userStatus()),
                    now
            );
        }
        return toDomain(users.saveAndFlush(entity));
    }

    @Override
    @Transactional
    public void recordLogin(long userId, String loginMethod) {
        var user = users.findById(userId).orElseThrow();
        user.recordLogin(loginMethod, Instant.now(clock));
        users.saveAndFlush(user);
    }

    private UserAccount toDomain(UserAccountJpaEntity entity) {
        var employee = entity.employee();
        return new UserAccount(
                entity.id(),
                employee.id(),
                entity.mobile(),
                entity.passwordHash(),
                EmploymentType.valueOf(employee.employmentType().toUpperCase(Locale.ROOT)),
                UserStatus.valueOf(entity.status().toUpperCase(Locale.ROOT)),
                EmployeeStatus.valueOf(employee.status().toUpperCase(Locale.ROOT)),
                employee.name(),
                employee.avatarUrl()
        );
    }

    private static String db(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
