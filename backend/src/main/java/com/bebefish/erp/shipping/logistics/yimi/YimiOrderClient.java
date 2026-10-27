package com.bebefish.erp.shipping.logistics.yimi;
import com.bebefish.erp.shipping.logistics.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class YimiOrderClient {
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final Clock clock;

    @Autowired
    public YimiOrderClient(ObjectMapper mapper, Clock clock) {
        this(mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build(), clock);
    }

    public YimiOrderClient(ObjectMapper mapper,HttpClient client,Clock clock) {
        if (client.followRedirects() != HttpClient.Redirect.NEVER)
            throw new IllegalArgumentException("壹米客户端必须禁止重定向");
        this.mapper = mapper; this.client = client; this.clock = clock;
    }

    public static String digest(String value) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public YimiOrderResult createOrder(Map<String,Object> body,YimiProperties account,String expectedOutsideNo) {
        if (account == null || !account.ready() || body == null || expectedOutsideNo == null || expectedOutsideNo.isBlank()
                || !expectedOutsideNo.equals(body.get("outSideNo")))
            return rejected("壹米下单资料或接口配置不完整");
        try {
            JsonNode root = post("electTabelSaveOrder", body, account);
            if (!validRoot(root)) return unknown();
            JsonNode data = root.path("data");
            if (!root.path("success").booleanValue()) {
                if (duplicate(root) || data.hasNonNull("wayBillNo")) return unknown();
                return rejected("壹米拒绝下单，请检查接口配置及订单资料");
            }
            if (!data.isObject() || !data.path("status").isIntegralNumber()) return unknown();
            if (data.path("status").bigIntegerValue().signum() != 0) {
                if (duplicate(data) || data.hasNonNull("wayBillNo")) return unknown();
                return rejected("壹米拒绝下单，请检查承运范围及订单资料");
            }
            String tracking = data.path("wayBillNo").asText("");
            if (!expectedOutsideNo.equals(data.path("outSideNo").asText()) || !waybill(tracking)
                    || body.containsKey("wayBillNo") && !tracking.equals(String.valueOf(body.get("wayBillNo")))) return unknown();
            JsonNode childOrders = data.path("subOrders");
            if (!childOrders.isMissingNode() && !childOrders.isNull() && !childOrders.isTextual()) return unknown();
            String children = childOrders.asText("");
            // Never expose a foreign child's tracking number returned alongside this order.
            if (!children.isEmpty() && !children.matches(tracking + "[0-9]{1,4}(," + tracking + "[0-9]{1,4})*")) return unknown();
            Map<String,Object> metadata = new LinkedHashMap<>();
            for (String key : List.of("sourceSiteCode","sourceSiteName","deptSiteCode","deptSiteName","allocationCentre","routeName","storageCode","serverType","productType")) {
                if (data.path(key).isValueNode() && !data.path(key).isNull()) metadata.put(key, safe(data.path(key).asText(),account));
            }
            return new YimiOrderResult(new AneOrderResult("succeeded",tracking,children,"已取得壹米运单号，请继续安排实际发货"),metadata);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); return unknown();
        } catch (Exception failure) {
            // No upstream body, signed envelope or exception text is logged or reflected.
            return unknown();
        }
    }

    public AneCancellationResult cancelOrder(Map<String,Object> body,YimiProperties account) {
        if (body == null || body.size() != 1 || !waybill(String.valueOf(body.get("wayBillNo"))) || !credentials(account))
            return new AneCancellationResult("cancel_rejected","壹米取消资料不完整，必须使用原运单号");
        try {
            JsonNode root = post("cancelOrder", body, account);
            if (!validRoot(root)) return cancelUnknown();
            JsonNode data = root.path("data");
            if (!data.isObject() || !data.path("success").isBoolean()) return cancelUnknown();
            String tracking = String.valueOf(body.get("wayBillNo"));
            if (data.hasNonNull("wayBillNo") && !tracking.equals(data.path("wayBillNo").asText())) return cancelUnknown();
            String summary = data.path("data").isTextual() ? data.path("data").asText().strip() : "";
            JsonNode errors = data.path("errorMessages");
            boolean noErrors = errors.isMissingNode() || errors.isNull() || errors.isTextual() && errors.asText().isBlank();
            if (root.path("success").booleanValue() && data.path("success").booleanValue() && noErrors
                    && summary.matches("1条处理成功[,，]0(?:条)?处理失败[。.]?"))
                return new AneCancellationResult("cancelled","壹米订单已取消，原运单不可用于走货；如需重新下单，请新建发货单");
            if (root.path("success").booleanValue() && !data.path("success").booleanValue()
                    && summary.matches("0条处理成功[,，]1(?:条)?处理失败[。.]?"))
                return new AneCancellationResult("cancel_rejected","壹米拒绝取消，请联系网点核实原订单");
            return cancelUnknown();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); return cancelUnknown();
        } catch (Exception failure) { return cancelUnknown(); }
    }

    public YimiTraceResult queryTrace(String waybillNo,YimiProperties account) {
        if (!waybill(waybillNo) || !credentials(account)) return traceUnavailable();
        try {
            JsonNode root = post("queryTrace", Map.of("wayBillNo",waybillNo), account);
            if (!validRoot(root) || !root.path("success").booleanValue()) return traceUnavailable();
            JsonNode data = root.path("data");
            if (!data.isObject() || !data.path("success").isBoolean()) return traceUnavailable();
            if (!data.path("success").booleanValue()) {
                String noInfo = data.path("errorMessages").asText("");
                return "无轨迹信息".equals(noInfo.strip()) ? traceEmpty() : traceUnavailable();
            }
            JsonNode orders = data.path("data");
            if (!orders.isArray()) return traceUnavailable();
            var events = new ArrayList<YimiTraceResult.TraceEvent>();
            for (JsonNode order : orders) {
                if (!waybillNo.equals(order.path("waybillNo").asText()) || !order.path("trackList").isArray()) continue;
                for (JsonNode event : order.path("trackList")) {
                    String eventWaybill = event.path("waybillNo").asText("");
                    if (!event.isObject() || !eventWaybill.isEmpty() && !waybillNo.equals(eventWaybill)) continue;
                    events.add(new YimiTraceResult.TraceEvent(safe(event.path("operateTime").asText(""),account),
                            safe(event.path("operTypeName").asText(""),account),safe(event.path("operDeptName").asText(""),account),
                            safe(event.path("description").asText(""),account)));
                }
            }
            return events.isEmpty() ? traceEmpty() : new YimiTraceResult("available","",events);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); return traceUnavailable();
        } catch (Exception failure) { return traceUnavailable(); }
    }

    private JsonNode post(String method,Map<String,Object> body,YimiProperties account) throws Exception {
        YimiProperties.validateGateway(account.gatewayUrl(),account.testEnvironment());
        Map<String,Object> envelope = new TreeMap<>();
        envelope.put("appKey",account.appKey()); envelope.put("appSecret",account.appSecret());
        envelope.put("messageBody",mapper.writeValueAsString(sorted(body))); envelope.put("method",method);
        envelope.put("timestamp",clock.millis()); envelope.put("version","1.0");
        envelope.put("digest",digest(mapper.writeValueAsString(envelope)));
        var request = HttpRequest.newBuilder(account.gatewayUrl()).timeout(Duration.ofSeconds(20))
                .header("Content-Type","application/json;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(envelope),StandardCharsets.UTF_8)).build();
        var response = client.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return response.statusCode() == 200 ? mapper.readTree(response.body()) : null;
    }
    private Object sorted(Object value) {
        if (value instanceof Map<?,?> values) {
            Map<String,Object> sorted = new TreeMap<>(); values.forEach((key,item) -> sorted.put(String.valueOf(key), sorted(item))); return sorted;
        }
        if (value instanceof List<?> values) return values.stream().map(this::sorted).toList();
        return value;
    }
    private boolean validRoot(JsonNode root) { return root != null && root.isObject() && root.path("success").isBoolean(); }
    private boolean duplicate(JsonNode root) {
        String text = root.toString().toLowerCase(Locale.ROOT);
        return text.contains("重复") || text.contains("已存在") || text.contains("duplicate") || text.contains("already exist");
    }
    private boolean credentials(YimiProperties account) { return account != null && !account.appKey().isBlank() && !account.appSecret().isBlank(); }
    private static boolean waybill(String value) { return value != null && value.matches("[0-9]{12}"); }
    private static String safe(String value,YimiProperties account) {
        String text = value.replaceAll("[\\p{Cntrl}]"," ");
        for (String secret : List.of(account.appKey(),account.appSecret())) if (!secret.isEmpty()) text = text.replace(secret,"[REDACTED]");
        return text.length() <= 2000 ? text : text.substring(0,2000);
    }
    private static YimiOrderResult rejected(String message) { return new YimiOrderResult(new AneOrderResult("rejected","","",message),Map.of()); }
    private static YimiOrderResult unknown() { return new YimiOrderResult(AneOrderResult.unknown("壹米下单结果待核实，请联系网点核对，勿重复下单"),Map.of()); }
    private static AneCancellationResult cancelUnknown() { return new AneCancellationResult("cancel_unknown","壹米取消结果待核实，请联系网点核对原订单和运单，勿重复取消或重新下单"); }
    private static YimiTraceResult traceEmpty() { return new YimiTraceResult("empty","暂无物流轨迹",List.of()); }
    private static YimiTraceResult traceUnavailable() { return new YimiTraceResult("unavailable","暂时无法获取壹米轨迹，请稍后重试",List.of()); }
}
