package com.bebefish.erp.logistics;

import static com.bebefish.erp.logistics.LogisticsDtos.*;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Resolves immutable, decrypted account snapshots for internal carrier operations only. */
@Service
public class CarrierAccountResolver {
    private final LogisticsCatalogService catalog;
    public CarrierAccountResolver(LogisticsCatalogService catalog) { this.catalog=catalog; }
    public Optional<ResolvedAccount> current(long companyId) { return catalog.currentResolved(companyId); }
    public ResolvedAccount resolve(long accountVersionId) { return catalog.resolveVersion(accountVersionId); }
    public record ResolvedAccount(long companyId,long accountId,long accountVersionId,String provider,
            String environment,AccountInput config) {
        @Override public String toString() { return "ResolvedAccount[companyId="+companyId+",accountId="+accountId+",accountVersionId="+accountVersionId+"]"; }
    }
}
