package com.bebefish.erp.shipping.domain;

import java.time.LocalDate;

public record ShipmentQuery(String keyword, String status, LocalDate dateFrom, LocalDate dateTo,
                            String platform, boolean incompleteOnly, Long platformId, Long shopId, boolean unlinkedOnly,
                            String logisticsCompany) {
    public ShipmentQuery(String keyword,String status,LocalDate dateFrom,LocalDate dateTo,String platform,boolean incompleteOnly,
                         Long platformId,Long shopId,boolean unlinkedOnly) {
        this(keyword,status,dateFrom,dateTo,platform,incompleteOnly,platformId,shopId,unlinkedOnly,null);
    }
    public ShipmentQuery(String keyword,String status,LocalDate dateFrom,LocalDate dateTo,String platform,boolean incompleteOnly) {
        this(keyword,status,dateFrom,dateTo,platform,incompleteOnly,null,null,false);
    }
}
