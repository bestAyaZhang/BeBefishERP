package com.bebefish.erp.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.bebefish.erp.auth.domain.AuthenticatedUser;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryTokenIssuerTest {
    private final InMemoryTokenIssuer issuer = new InMemoryTokenIssuer();

    @Test
    void storesResolvedRolesAndPermissionsInTheSessionSnapshot() {
        var issued = issuer.issue(new AuthenticatedUser(
                "13800138000",
                List.of("SUPER_ADMIN"),
                List.of("system:role:view", "system:role:manage")
        ), "password");

        assertThat(issuer.resolve(issued.accessToken()).roles()).containsExactly("SUPER_ADMIN");
        assertThat(issuer.resolve(issued.accessToken()).permissions())
                .containsExactly("system:role:view", "system:role:manage");
    }
}
