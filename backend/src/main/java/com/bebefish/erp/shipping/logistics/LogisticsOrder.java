package com.bebefish.erp.shipping.logistics;

import java.time.LocalDateTime;

public record LogisticsOrder(long shipmentId, String orderNo, String state, String trackingNo,
                             String childTrackingNos, String message, LocalDateTime updatedAt, boolean testEnvironment,String provider) {
    public LogisticsOrder(long shipmentId,String orderNo,String state,String trackingNo,String childTrackingNos,
            String message,LocalDateTime updatedAt,boolean testEnvironment) {
        this(shipmentId,orderNo,state,trackingNo,childTrackingNos,message,updatedAt,testEnvironment,"ANE");
    }
}
