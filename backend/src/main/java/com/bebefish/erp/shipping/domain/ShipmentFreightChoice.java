package com.bebefish.erp.shipping.domain;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** An issued server quote is the only accepted price authority. */
public record ShipmentFreightChoice(@Size(max=36) String quoteId, @Positive Long preferredCompanyId, boolean clear) {}
