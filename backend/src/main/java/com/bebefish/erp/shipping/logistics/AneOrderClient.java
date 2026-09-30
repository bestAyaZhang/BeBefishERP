package com.bebefish.erp.shipping.logistics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AneOrderClient {
    private static final Logger log = LoggerFactory.getLogger(AneOrderClient.class);
    private final AneProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public AneOrderClient(AneProperties properties, ObjectMapper mapper) { this.properties = properties; this.mapper = mapper; }

    public AneOrderResult createOrder(Map<String, Object> params) {
        try {
            String json = mapper.writeValueAsString(params);
            String payload = mapper.writeValueAsString(Map.of("params", json, "code", properties.getCode(),
                    "timestamp", String.valueOf(System.currentTimeMillis()), "digest", digest(json, properties.getCode(), properties.getAppKey())));
            var request = HttpRequest.newBuilder(properties.getOrderUrl()).timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json;charset=UTF-8").POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8)).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) return AneOrderResult.unknown("安能响应异常，下单结果待核实，请勿重复下单");
            return parse(response.body(), String.valueOf(params.get("orderNo")));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return AneOrderResult.unknown("请求中断，下单结果待核实，请勿重复下单");
        } catch (Exception failure) {
            // Never log signed payloads or upstream response bodies: they can contain account credentials.
            return AneOrderResult.unknown("请求超时或响应异常，下单结果待核实，请联系物流核对订单号");
        }
    }

    public AneCancellationResult cancelOrder(Map<String, Object> params) {
        Integer httpStatus = null;
        try {
            String json = mapper.writeValueAsString(params);
            String payload = mapper.writeValueAsString(Map.of("params", json, "code", properties.getCode(),
                    "timestamp", String.valueOf(System.currentTimeMillis()), "digest", digest(json, properties.getCode(), properties.getAppKey())));
            var request = HttpRequest.newBuilder(properties.getUpdateUrl()).timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8)).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            httpStatus = response.statusCode();
            var result = httpStatus != 200 ? AneCancellationResult.unknown("安能 HTTP " + httpStatus)
                    : parseCancellation(response.body(), String.valueOf(params.get("orderNo")));
            return observeCancellation(params, result, httpStatus, "none");
        } catch (HttpTimeoutException timeout) {
            return observeCancellation(params, AneCancellationResult.unknown("请求超时，未收到明确取消结果"), httpStatus, "timeout");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return observeCancellation(params, AneCancellationResult.unknown("请求中断，未收到明确取消结果"), httpStatus, "interrupted");
        } catch (IOException networkFailure) {
            return observeCancellation(params, AneCancellationResult.unknown("网络异常，未收到有效安能响应"), httpStatus, "io_failure");
        } catch (Exception failure) {
            return observeCancellation(params, AneCancellationResult.unknown("取消请求异常"), httpStatus, "request_failure");
        }
    }

    private AneCancellationResult observeCancellation(Map<String, Object> params, AneCancellationResult result,
            Integer httpStatus, String failureType) {
        if ("cancel_unknown".equals(result.state())) {
            // Only fixed diagnostics, numeric codes and a hashed reference; never log payloads, bodies or exception messages.
            String reference = UUID.nameUUIDFromBytes(String.valueOf(params.get("orderNo")).getBytes(StandardCharsets.UTF_8)).toString();
            log.warn("ANE cancellation uncertain: orderRef={}, environment={}, HTTP={}, failureType={}, detail={}",
                    reference, properties.testEnvironment() ? "test" : "production", httpStatus, failureType, result.message());
        }
        return result;
    }

    AneCancellationResult parseCancellation(String body, String orderNo) {
        JsonNode root;
        try { root = mapper.readTree(body); }
        catch (JsonProcessingException invalidJson) { return AneCancellationResult.unknown("安能响应不是有效 JSON"); }
        if (root == null || !root.isObject()) return AneCancellationResult.unknown("安能响应结构异常");
        String code = root.path("resultCode").asText();
        if (!code.matches("[0-9]{4}") || !root.path("result").isBoolean())
            return AneCancellationResult.unknown("安能响应缺少有效 result / resultCode");
        String diagnosticCode = "，返回码 " + code;
        JsonNode info = root.path("resultInfo");
        if (info.isTextual() && !info.asText().isBlank()) {
            try { info = mapper.readTree(info.asText()); }
            catch (JsonProcessingException invalidInfo) { return AneCancellationResult.unknown("resultInfo 不是有效 JSON" + diagnosticCode); }
        }
        if (!info.isMissingNode() && !info.isNull() && !(info.isTextual() && info.asText().isBlank())) {
            if (!info.isObject()) return AneCancellationResult.unknown("resultInfo 结构异常" + diagnosticCode);
            if (info.path("orderNo").asText().isBlank()) return AneCancellationResult.unknown("resultInfo 缺少订单号" + diagnosticCode);
            if (!orderNo.equals(info.path("orderNo").asText())) return AneCancellationResult.unknown("安能响应订单号不匹配" + diagnosticCode);
        }
        if (root.path("result").isBoolean() && root.path("result").asBoolean() && "1000".equals(code)) {
            return new AneCancellationResult("cancelled", "安能订单已取消，原运单不可用于走货；如需重新下单，请新建发货单");
        }
        if (root.path("result").isBoolean() && !root.path("result").asBoolean()
                && Set.of("1001", "3001", "2001", "2002", "2003").contains(code)) {
            String reason = root.path("reason").asText("").replaceAll("[\\p{Cntrl}]", " ").strip();
            if (reason.length() > 200) reason = reason.substring(0, 200);
            return new AneCancellationResult("cancel_rejected", "安能拒绝取消，原订单仍有效（返回码 " + code
                    + (reason.isBlank() ? "" : "：" + reason) + "），请核实是否已揽收或联系网点处理");
        }
        return AneCancellationResult.unknown("安能未明确确认取消" + diagnosticCode);
    }

    AneOrderResult parse(String body, String orderNo) throws Exception {
        JsonNode root = mapper.readTree(body);
        String code = root.path("resultCode").asText();
        if (!root.path("result").asBoolean(false) || !"1000".equals(code)) {
            String message = switch (code) {
                case "2001" -> "安能客户标识未通过验证，请检查服务端账号配置";
                case "2002" -> "安能签名验证失败，请检查服务端密钥配置";
                case "2003" -> "安能时间戳验证失败，请检查服务器时间";
                default -> "安能未返回可承运运单，请联系网点核实订单、承运范围及面单额度，勿重复下单";
            };
            String reason = root.path("reason").asText("").replaceAll("[\\p{Cntrl}]", " ").strip();
            if (reason.length() > 200) reason = reason.substring(0, 200);
            String diagnostic = code.isBlank() ? "" : "（安能返回码 " + code + (reason.isBlank() ? "" : "：" + reason) + "）";
            // The document says a failed GIS match can still create an order. Only explicit auth failures are retryable.
            return new AneOrderResult(Set.of("2001", "2002", "2003").contains(code) ? "rejected" : "unknown", "", "", message + diagnostic);
        }
        JsonNode info = root.path("resultInfo");
        if (info.isTextual()) info = mapper.readTree(info.asText());
        String tracking = info.path("ewbNo").asText("");
        if (!orderNo.equals(info.path("orderNo").asText()) || !"Y".equals(info.path("matchResult").asText())
                || !tracking.matches("[0-9]{12}")) {
            return AneOrderResult.unknown("安能未返回匹配的可承运运单号，请联系网点核实，勿重复下单");
        }
        return new AneOrderResult("succeeded", tracking, info.path("childEwbNo").asText(""), "已取得安能运单号，请继续安排实际发货");
    }

    static String digest(String params, String code, String key) {
        try {
            byte[] bytes = MessageDigest.getInstance("MD5").digest((params + code + key).getBytes(StandardCharsets.UTF_8));
            String hex = HexFormat.of().formatHex(bytes);
            return Base64.getEncoder().encodeToString(hex.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
