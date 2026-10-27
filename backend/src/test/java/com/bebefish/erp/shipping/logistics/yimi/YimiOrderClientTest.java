package com.bebefish.erp.shipping.logistics.yimi;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class YimiOrderClientTest {
    final ObjectMapper mapper = new ObjectMapper();
    HttpClient transport;
    YimiOrderClient client;
    final YimiProperties account = new YimiProperties(true, null, "test-key", "test-secret", "seller", "subscription");
    String sent;
    HttpRequest request;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() throws Exception {
        transport = mock(HttpClient.class);
        when(transport.followRedirects()).thenReturn(HttpClient.Redirect.NEVER);
        client = new YimiOrderClient(mapper, transport, Clock.fixed(Instant.ofEpochMilli(1700000000123L), ZoneOffset.UTC));
        reply("{\"success\":true,\"data\":{\"status\":0,\"outSideNo\":\"BF1\",\"wayBillNo\":\"123456789012\",\"subOrders\":\"123456789012001\",\"routeName\":\"route\"}}");
    }

    @SuppressWarnings("unchecked")
    void reply(String body) throws Exception {
        var response = (HttpResponse<String>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body);
        when(transport.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> {
            request = call.getArgument(0);
            var collected = new java.io.ByteArrayOutputStream();
            var done = new CompletableFuture<Void>();
            request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
                public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
                public void onNext(ByteBuffer buffer) { byte[] data = new byte[buffer.remaining()]; buffer.get(data); collected.writeBytes(data); }
                public void onError(Throwable failure) { done.completeExceptionally(failure); }
                public void onComplete() { done.complete(null); }
            });
            done.join(); sent = collected.toString(StandardCharsets.UTF_8);
            return response;
        });
    }

    @Test void signsSortedPublicJsonWithRawMd5AndPostsSingleElectronicMethod() throws Exception {
        assertThat(YimiOrderClient.digest("1")).isEqualTo("xMpCOKC5I4INzFCab3WEmw==");
        var result = client.createOrder(Map.of("outSideNo", "BF1"), account, "BF1");
        assertThat(result.order().state()).isEqualTo("succeeded");
        assertThat(result.order().trackingNo()).isEqualTo("123456789012");
        assertThat(result.order().childTrackingNos()).isEqualTo("123456789012001");
        assertThat(result.metadata()).containsEntry("routeName", "route");
        assertThat(sent).isEqualTo("{\"appKey\":\"test-key\",\"appSecret\":\"test-secret\",\"digest\":\"hbmlfs72FJe78KLF0hYxOQ==\",\"messageBody\":\"{\\\"outSideNo\\\":\\\"BF1\\\"}\",\"method\":\"electTabelSaveOrder\",\"timestamp\":1700000000123,\"version\":\"1.0\"}");
        assertThat(request.uri()).isEqualTo(account.gatewayUrl());
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.headers().firstValue("Content-Type")).contains("application/json;charset=UTF-8");
    }

    @Test void onlyMatchingOutsideNumberAndTwelveDigitWaybillCanSucceed() throws Exception {
        for (String data : new String[]{"{\"status\":0,\"outSideNo\":\"OTHER\",\"wayBillNo\":\"123456789012\"}",
                "{\"status\":0,\"outSideNo\":\"BF1\",\"wayBillNo\":\"123\"}",
                "{\"status\":\"0\",\"outSideNo\":\"BF1\",\"wayBillNo\":\"123456789012\"}",
                "{\"outSideNo\":\"BF1\",\"wayBillNo\":\"123456789012\"}",
                "{\"status\":4294967296,\"outSideNo\":\"BF1\",\"wayBillNo\":\"123456789012\"}"}) {
            reply("{\"success\":true,\"data\":" + data + "}");
            var result = client.createOrder(Map.of("outSideNo", "BF1"), account, "BF1");
            assertThat(result.order().state()).isEqualTo("unknown");
            assertThat(result.order().trackingNo()).isEmpty();
            assertThat(result.metadata()).isEmpty();
        }
    }

    @Test void doesNotAcceptForeignChildOrSpecifiedWaybillAndOnlyRetainsSafeRoutingMetadata() throws Exception {
        assertThat(client.createOrder(Map.of("outSideNo","BF1","wayBillNo","111111111111"),account,"BF1").order().state()).isEqualTo("unknown");
        reply("{\"success\":true,\"data\":{\"status\":0,\"outSideNo\":\"BF1\",\"wayBillNo\":\"123456789012\",\"subOrders\":\"999999999999001\"}}");
        assertThat(client.createOrder(Map.of("outSideNo","BF1"),account,"BF1").order().state()).isEqualTo("unknown");
        reply("{\"success\":true,\"data\":{\"status\":0,\"outSideNo\":\"BF1\",\"wayBillNo\":\"123456789012\",\"appSecret\":\"test-secret\",\"sourceSiteName\":\"test-secret test-key\"}}");
        var result = client.createOrder(Map.of("outSideNo","BF1"),account,"BF1");
        assertThat(result.order().state()).isEqualTo("succeeded");
        assertThat(result.metadata()).doesNotContainKey("appSecret");
        assertThat(result.toString()).doesNotContain("test-secret","test-key");
    }

    @Test void validatesMissingConfigurationAndCorrelationBeforeSendingAnything() throws Exception {
        assertThat(client.createOrder(Map.of("outSideNo","BF2"),account,"BF1").order().state()).isEqualTo("rejected");
        assertThat(client.createOrder(Map.of("outSideNo","BF1"),new YimiProperties(true,null,"test-key","test-secret","",""),"BF1").order().state()).isEqualTo("rejected");
        verify(transport,never()).send(any(),any(HttpResponse.BodyHandler.class));
    }

    @Test void supportsChildSuffixAtTheDocumentedThousandPieceBoundary() throws Exception {
        reply("{\"success\":true,\"data\":{\"status\":0,\"outSideNo\":\"BF1\",\"wayBillNo\":\"123456789012\",\"subOrders\":\"1234567890121000\"}}");
        var result = client.createOrder(Map.of("outSideNo","BF1"),account,"BF1");
        assertThat(result.order().state()).isEqualTo("succeeded");
        assertThat(result.order().childTrackingNos()).isEqualTo("1234567890121000");
    }

    @Test void nonTextualChildWaybillsNeverConfirmOrderSuccess() throws Exception {
        for (String children : new String[]{"[\"999999999999001\"]", "{\"waybillNo\":\"999999999999001\"}", "[]", "{}", "123456789012001", "false"}) {
            reply("{\"success\":true,\"data\":{\"status\":0,\"outSideNo\":\"BF1\",\"wayBillNo\":\"123456789012\",\"subOrders\":" + children + "}}");
            var result = client.createOrder(Map.of("outSideNo","BF1"),account,"BF1");
            assertThat(result.order().state()).as("subOrders=%s",children).isEqualTo("unknown");
            assertThat(result.order().trackingNo()).isEmpty();
            assertThat(result.order().childTrackingNos()).isEmpty();
            assertThat(result.metadata()).isEmpty();
        }
    }

    @Test void missingNullAndCommaSeparatedTextChildWaybillsRemainSupported() throws Exception {
        for (String[] fixture : new String[][]{{"",""}, {",\"subOrders\":null",""}, {",\"subOrders\":\"\"",""},
                {",\"subOrders\":\"123456789012001,123456789012002\"","123456789012001,123456789012002"}}) {
            reply("{\"success\":true,\"data\":{\"status\":0,\"outSideNo\":\"BF1\",\"wayBillNo\":\"123456789012\"" + fixture[0] + "}}");
            var result = client.createOrder(Map.of("outSideNo","BF1"),account,"BF1");
            assertThat(result.order().state()).isEqualTo("succeeded");
            assertThat(result.order().trackingNo()).isEqualTo("123456789012");
            assertThat(result.order().childTrackingNos()).isEqualTo(fixture[1]);
        }
    }

    @Test void explicitRejectionIsSafeButDuplicateOrMalformedResponsesRemainUnknown() throws Exception {
        reply("{\"success\":false,\"errorMsg\":\"test-secret test-key\"}");
        var rejected = client.createOrder(Map.of("outSideNo", "BF1"), account, "BF1");
        assertThat(rejected.order().state()).isEqualTo("rejected");
        assertThat(rejected.toString()).doesNotContain("test-secret", "test-key");
        for (String body : new String[]{"bad", "null", "{\"success\":true}",
                "{\"success\":true,\"data\":{\"status\":1,\"errorMsg\":\"重复订单\"}}",
                "{\"success\":false,\"errorMsg\":\"订单已存在\"}"}) {
            reply(body);
            assertThat(client.createOrder(Map.of("outSideNo", "BF1"), account, "BF1").order().state()).isEqualTo("unknown");
        }
        reply("{\"success\":true,\"data\":{\"status\":1,\"errorMsg\":\"地址不完整 test-secret\"}}");
        assertThat(client.createOrder(Map.of("outSideNo", "BF1"), account, "BF1").order().state()).isEqualTo("rejected");
    }

    @Test void timeoutNeverRetriesAndNeverExposesTransportSecrets() throws Exception {
        when(transport.send(any(), any(HttpResponse.BodyHandler.class))).thenThrow(new HttpTimeoutException("test-secret"));
        var result = client.createOrder(Map.of("outSideNo", "BF1"), account, "BF1");
        assertThat(result.order().state()).isEqualTo("unknown");
        assertThat(result.toString()).doesNotContain("test-secret", "test-key");
        verify(transport, times(1)).send(any(), any(HttpResponse.BodyHandler.class));
    }

    @Test void cancellationUsesOnlyFrozenWaybillAndRequiresExactlyOneSuccessfulItem() throws Exception {
        reply("{\"success\":true,\"data\":{\"success\":true,\"data\":\"1条处理成功,0处理失败。\",\"errorMessages\":\"\"}}");
        assertThat(client.cancelOrder(Map.of("wayBillNo", "123456789012"), account).state()).isEqualTo("cancelled");
        assertThat(mapper.readTree(sent).path("method").asText()).isEqualTo("cancelOrder");
        assertThat(mapper.readTree(mapper.readTree(sent).path("messageBody").asText())).isEqualTo(mapper.readTree("{\"wayBillNo\":\"123456789012\"}"));
        for (String summary : new String[]{"2条处理成功,0处理失败。", "1条处理成功,1处理失败。", "处理成功", "11条处理成功,0处理失败。"}) {
            reply("{\"success\":true,\"data\":{\"success\":true,\"data\":\"" + summary + "\"}}");
            assertThat(client.cancelOrder(Map.of("wayBillNo", "123456789012"), account).state()).isEqualTo("cancel_unknown");
        }
        reply("{\"success\":true,\"data\":{\"success\":false,\"data\":\"0条处理成功,1处理失败。\"}}");
        assertThat(client.cancelOrder(Map.of("wayBillNo", "123456789012"), account).state()).isEqualTo("cancel_rejected");
        clearInvocations(transport);
        assertThat(client.cancelOrder(Map.of("orderNos", "123456789012"), account).state()).isEqualTo("cancel_rejected");
        verify(transport, never()).send(any(), any(HttpResponse.BodyHandler.class));
    }

    @Test void contradictoryCancellationErrorsOrForeignWaybillNeverConfirmCancellation() throws Exception {
        for (String extra : new String[]{"\"errorMessages\":[\"failed\"]", "\"errorMessages\":{\"failure\":true}", "\"wayBillNo\":\"999999999999\""}) {
            reply("{\"success\":true,\"data\":{\"success\":true,\"data\":\"1条处理成功,0处理失败。\"," + extra + "}}");
            assertThat(client.cancelOrder(Map.of("wayBillNo","123456789012"),account).state()).isEqualTo("cancel_unknown");
        }
    }

    @Test void nonSuccessHttpAndInterruptsRemainUncertain() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(302);
        when(transport.send(any(),any(HttpResponse.BodyHandler.class))).thenReturn(response);
        assertThat(client.createOrder(Map.of("outSideNo","BF1"),account,"BF1").order().state()).isEqualTo("unknown");
        assertThat(client.cancelOrder(Map.of("wayBillNo","123456789012"),account).state()).isEqualTo("cancel_unknown");
        assertThat(client.queryTrace("123456789012",account).status()).isEqualTo("unavailable");
        when(transport.send(any(),any(HttpResponse.BodyHandler.class))).thenThrow(new InterruptedException("test-secret"));
        try {
            assertThat(client.createOrder(Map.of("outSideNo","BF1"),account,"BF1").order().state()).isEqualTo("unknown");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally { Thread.interrupted(); }
    }

    @Test void traceReadsNestedMatchingTrackListAndFiltersForeignIdentifiers() throws Exception {
        reply("{\"success\":true,\"data\":{\"success\":true,\"data\":[{\"waybillNo\":\"999999999999\",\"trackList\":[{\"description\":\"foreign\"}]},{\"waybillNo\":\"123456789012\",\"trackList\":[{\"waybillNo\":null,\"operateTime\":\"2026-10-27T01:00:00Z\",\"operTypeName\":\"开单\",\"operDeptName\":\"上海网点\",\"description\":\"已揽收\"},{\"waybillNo\":\"999999999999\",\"description\":\"foreign child\"}]}]}}");
        var trace = client.queryTrace("123456789012", account);
        assertThat(trace.status()).isEqualTo("available");
        assertThat(trace.events()).containsExactly(new YimiTraceResult.TraceEvent("2026-10-27T01:00:00Z", "开单", "上海网点", "已揽收"));
        assertThat(mapper.readTree(sent).path("method").asText()).isEqualTo("queryTrace");
        assertThat(trace.toString()).doesNotContain("foreign");
    }

    @Test void traceDistinguishesNoInfoFromBusinessOrTransportFailure() throws Exception {
        reply("{\"success\":true,\"data\":{\"success\":false,\"data\":null,\"errorMessages\":\"无轨迹信息\"}}");
        assertThat(client.queryTrace("123456789012", account).status()).isEqualTo("empty");
        reply("{\"success\":false,\"errorMsg\":\"test-secret\"}");
        assertThat(client.queryTrace("123456789012", account).status()).isEqualTo("unavailable");
        reply("{\"success\":true,\"data\":{\"success\":false,\"errorMessages\":\"无权限 test-secret\"}}");
        assertThat(client.queryTrace("123456789012", account).status()).isEqualTo("unavailable");
    }

    @Test void strictGatewayAndRedirectPolicyRejectUntrustedDestinations() {
        for (String url : new String[]{"http://yh-uop-test.yimidida.com/uce-uop-main/api/gateway", "https://evil.test/uce-uop-main/api/gateway", "https://yh-uop-test.yimidida.com:443/uce-uop-main/api/gateway", "https://yh-uop-test.yimidida.com/uce-uop-main/api/gateway?x=1", "https://yh-uop.uce.cn/uce-uop-main/api/gateway"}) {
            assertThatThrownBy(() -> new YimiProperties(true, URI.create(url), "key", "secret", "seller", "subscription")).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(account.toString()).doesNotContain("test-key", "test-secret");
        assertThat(account.ready()).isTrue();
        assertThat(new YimiProperties(true, null, "key", "secret", "", "").ready()).isFalse();
        when(transport.followRedirects()).thenReturn(HttpClient.Redirect.ALWAYS);
        assertThatThrownBy(() -> new YimiOrderClient(mapper, transport, Clock.systemUTC())).isInstanceOf(IllegalArgumentException.class);
    }
}
