package com.bebefish.erp.shipping.logistics.yimi;
import com.bebefish.erp.shipping.logistics.AneOrderResult;
import java.util.Map;
public record YimiOrderResult(AneOrderResult order, Map<String,Object> metadata) {
    public YimiOrderResult { metadata = metadata == null ? Map.of() : Map.copyOf(metadata); }
}
