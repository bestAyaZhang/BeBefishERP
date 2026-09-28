package com.bebefish.erp.shipping.logistics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AneOrderClient {
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
