package com.bebefish.erp.shipping.logistics.yimi;
import java.util.List;
public record YimiTraceResult(String status,String message,List<TraceEvent> events) {
    public YimiTraceResult { events = events == null ? List.of() : List.copyOf(events); }
    public record TraceEvent(String time,String status,String location,String description) { }
}
