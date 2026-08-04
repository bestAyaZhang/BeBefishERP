package com.bebefish.erp.sales.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VoidSalesOrderRequest(String reason) {
}
