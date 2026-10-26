package com.bebefish.erp.logistics.pricing;

import java.math.BigDecimal;
import java.util.List;

/** JSON-compatible contract for the database-independent freight engine. */
public final class FreightRules {
    private FreightRules() {}

    public record Band(BigDecimal lowerExclusive, BigDecimal upperInclusive,
                       BigDecimal firstQuantity, BigDecimal firstFee,
                       BigDecimal additionalQuantity, BigDecimal additionalFee) {}

    public record RegionGroup(String name, List<String> provinces, List<Band> bands) {}

    public record Rules(String mode, boolean volumetric, BigDecimal volumeDivisor,
                        List<Band> defaultBands, List<RegionGroup> regionGroups) {}

    public record Input(String destinationProvince, BigDecimal weight,
                        BigDecimal volume, BigDecimal pieces) {}

    public record Result(boolean available, String reason, BigDecimal amount,
                         BigDecimal billedQuantity, String matchedRegion,
                         Band band, Long renewalCount) {}
}
