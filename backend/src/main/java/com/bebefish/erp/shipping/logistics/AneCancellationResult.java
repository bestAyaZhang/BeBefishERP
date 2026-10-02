package com.bebefish.erp.shipping.logistics;

public record AneCancellationResult(String state, String message) {
    public static AneCancellationResult unknown() {
        return unknown("");
    }

    public static AneCancellationResult unknown(String diagnostic) {
        return new AneCancellationResult("cancel_unknown", "取消结果待核实"
                + (diagnostic.isBlank() ? "" : "（" + diagnostic + "）")
                + "，请联系安能网点核对原订单和运单，勿重复取消或重新下单");
    }
}
