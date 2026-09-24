package com.bebefish.erp.shipping.logistics;

public record AneOrderResult(String state, String trackingNo, String childTrackingNos, String message) {
    public static AneOrderResult unknown(String message) { return new AneOrderResult("unknown", "", "", message); }
}
