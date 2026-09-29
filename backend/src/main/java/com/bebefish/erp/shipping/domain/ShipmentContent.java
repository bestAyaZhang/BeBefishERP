package com.bebefish.erp.shipping.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ShipmentContent(
        LocalDate shipmentDate,
        String platform,
        String shopName,
        List<String> preparers,
        String recipientName,
        String recipientPhone,
        String recipientProvince,
        String recipientCity,
        String recipientCounty,
        String recipientDetailAddress,
        String preparationContent,
        String remark,
        BigDecimal estimatedFreight,
        AneOrderDraft orderDraft,
        String status,
        String orderer,
        String logisticsCompany,
        String trackingNo,
        Long platformId,
        Long shopId
) {
    public ShipmentContent(LocalDate shipmentDate,String platform,String shopName,List<String> preparers,String recipientName,
        String recipientPhone,String recipientProvince,String recipientCity,String recipientCounty,String recipientDetailAddress,
        String preparationContent,String remark,BigDecimal estimatedFreight,AneOrderDraft orderDraft,String status,
        String orderer,String logisticsCompany,String trackingNo) {
        this(shipmentDate,platform,shopName,preparers,recipientName,recipientPhone,recipientProvince,recipientCity,
            recipientCounty,recipientDetailAddress,preparationContent,remark,estimatedFreight,orderDraft,status,orderer,logisticsCompany,trackingNo,null,null);
    }
    public ShipmentContent {
        preparers = preparers == null ? List.of() : List.copyOf(preparers);
    }

    public static ShipmentContent from(LocalDate shipmentDate, ShipmentFormInput form, String status, String orderer,
                                       String logisticsCompany, String trackingNo) {
        return new ShipmentContent(shipmentDate, form.platform(), form.shopName(), form.preparers(), form.recipientName(),
                form.recipientPhone(), form.recipientProvince(), form.recipientCity(), form.recipientCounty(),
                form.recipientDetailAddress(), form.preparationContent(), form.remark(), form.estimatedFreight(),
                form.orderDraft(), status, orderer, clean(logisticsCompany), clean(trackingNo), form.platformId(), form.shopId());
    }

    public ShipmentFormInput form() {
        return new ShipmentFormInput(platform, shopName, preparers, recipientName, recipientPhone, recipientProvince,
                recipientCity, recipientCounty, recipientDetailAddress, preparationContent, remark, estimatedFreight,
                orderDraft, platformId, shopId);
    }

    public String recipientFullAddress() {
        return recipientProvince + recipientCity + recipientCounty + recipientDetailAddress;
    }

    private static String clean(String value) { return value == null ? "" : value.strip(); }
}
