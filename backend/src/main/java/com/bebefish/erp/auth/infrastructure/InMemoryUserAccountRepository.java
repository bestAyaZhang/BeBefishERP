package com.bebefish.erp.auth.infrastructure;

import com.bebefish.erp.auth.domain.UserAccount;
import com.bebefish.erp.auth.domain.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryUserAccountRepository implements UserAccountRepository {
    private final Clock clock;
    private final Map<String, StoredAccount> accounts = new ConcurrentHashMap<>();

    public InMemoryUserAccountRepository(Clock clock, PasswordEncoder passwordEncoder) {
        this.clock = clock;
        var admin = new UserAccount(
                "13800138000",
                passwordEncoder.encode("Admin@123456"),
                true,
                true,
                List.of("ADMIN"),
                List.of(
                        "system:user:view",
                        "system:role:view",
                        "dashboard:view",
                        "masterdata:view",
                        "masterdata:edit",
                        "product:view",
                        "product:edit",
                        "inventory:view",
                        "inventory:adjust",
                        "sales:view",
                        "sales:create",
                        "sales:confirm",
                        "sales:void",
                        "sales:print",
                        "finance:view",
                        "finance:receipt"
                )
        );
        accounts.put(admin.mobile(), new StoredAccount(admin, null, null));
    }

    @Override
    public Optional<UserAccount> findByMobile(String mobile) {
        return Optional.ofNullable(accounts.get(mobile)).map(StoredAccount::user);
    }

    @Override
    public void recordLogin(String mobile, String loginMethod) {
        accounts.computeIfPresent(mobile, (key, current) ->
                new StoredAccount(current.user(), loginMethod, Instant.now(clock))
        );
    }

    private record StoredAccount(UserAccount user, String lastLoginMethod, Instant lastLoginAt) {
    }
}
