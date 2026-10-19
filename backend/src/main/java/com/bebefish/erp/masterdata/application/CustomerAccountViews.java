package com.bebefish.erp.masterdata.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class CustomerAccountViews {
    private CustomerAccountViews() {}
    public record PlatformOption(long id, String name, String status) {}
    public record Price(long skuId, String itemNo, String productName, String skuCode, String skuName,
                        String salesUnit, String status, BigDecimal defaultSalePrice, BigDecimal unitPrice) {}
    public record Bill(long id, String orderNo, LocalDate orderDate, BigDecimal totalAmount,
                       BigDecimal receivedAmount, BigDecimal outstandingAmount) {}
    public record Summary(long orderCount, BigDecimal totalAmount, BigDecimal receivedAmount,
                          BigDecimal outstandingAmount) {}
    public record Bills(List<Bill> records, int page, int pageSize, long total, Summary summary) {}
}
