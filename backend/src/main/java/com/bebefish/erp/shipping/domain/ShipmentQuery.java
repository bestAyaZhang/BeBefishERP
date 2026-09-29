package com.bebefish.erp.shipping.domain;

import java.time.LocalDate;

public record ShipmentQuery(String keyword, String status, LocalDate dateFrom, LocalDate dateTo,
                            String platform, boolean incompleteOnly, Long platformId, Long shopId, boolean unlinkedOnly) {
    public ShipmentQuery(String keyword,String status,LocalDate dateFrom,LocalDate dateTo,String platform,boolean incompleteOnly) {
        this(keyword,status,dateFrom,dateTo,platform,incompleteOnly,null,null,false);
    }
}
