package com.bebefish.erp.shipping.api;

import com.bebefish.erp.common.api.GlobalExceptionHandler;
import com.bebefish.erp.common.security.ErpPrincipal;
import com.bebefish.erp.shipping.application.ShipmentService;
import com.bebefish.erp.shipping.domain.*;
import com.bebefish.erp.shipping.logistics.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.validation.Validation;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ShipmentControllerTest {
    private AnnotationConfigApplicationContext context;
    private MockMvc mvc;
    private ShipmentRepository repository;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean ShipmentRepository repository() { return mock(ShipmentRepository.class); }
        @Bean Clock clock() { return Clock.fixed(Instant.parse("2026-09-24T02:00:00Z"), ZoneId.of("Asia/Shanghai")); }
        @Bean ShipmentEditPolicy shipmentEditPolicy() { return new ShipmentEditPolicy(); }
        @Bean ShipmentService service(ShipmentRepository repository, Clock clock, ShipmentEditPolicy policy) {
            return new ShipmentService(repository, Validation.buildDefaultValidatorFactory().getValidator(), clock, policy);
        }
        @Bean ShipmentController controller(ShipmentService service) { return new ShipmentController(service); }
        @Bean AneOrderService logisticsService() { return mock(AneOrderService.class); }
        @Bean LogisticsOrderController logisticsController(AneOrderService logistics, ShipmentService service) {
            return new LogisticsOrderController(logistics, service);
        }
    }

    @BeforeEach
    void setup() {
        context = new AnnotationConfigApplicationContext(Config.class);
        repository = context.getBean(ShipmentRepository.class);
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(ShipmentController.class), context.getBean(LogisticsOrderController.class))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        when(repository.insert(any())).thenAnswer(call -> {
            Shipment s = call.getArgument(0);
            return new Shipment(17L, s.shipmentNo(), s.content(), 0, s.createdBy(), s.updatedBy(), s.createdAt(), s.updatedAt());
        });
    }

    @AfterEach
    void cleanup() { SecurityContextHolder.clearContext(); context.close(); }

    @Test
    void createUsesServerDefaultsAndAuthenticatedDisplayName() throws Exception {
        login("测试用户", "shipping:create");
        mvc.perform(post("/api/shipments").contentType(APPLICATION_JSON).content(body(validForm("测试收件人"), null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(17))
                .andExpect(jsonPath("$.data.createdBy").value("employee:42"))
                .andExpect(jsonPath("$.data.content.shipmentDate").value("2026-09-24"))
                .andExpect(jsonPath("$.data.content.status").value("unfinished"))
                .andExpect(jsonPath("$.data.content.orderer").value("测试用户"))
                .andExpect(jsonPath("$.data.content.preparers[0]").value("小周"))
                .andExpect(jsonPath("$.data.content.preparationContent").value("蓝色盒子 2个\n纸箱 1个"));
    }

    @Test
    void readPermissionCannotCreateOrEdit() throws Exception {
        login("查看人", "shipping:view");
        mvc.perform(post("/api/shipments").contentType(APPLICATION_JSON).content(body(validForm("测试"), null, null)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/shipments/17").contentType(APPLICATION_JSON)
                .content(body(validForm("测试"), "unfinished", 0L))).andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsMissingShopTooManyPreparersAndMissingRegion() throws Exception {
        login("测试用户", "shipping:create");
        var missingShop = validForm("测试");
        missingShop.put("shopName", "  ");
        mvc.perform(post("/api/shipments").contentType(APPLICATION_JSON).content(body(missingShop, null, null)))
                .andExpect(status().isBadRequest());

        var tooManyPreparers = validForm("测试");
        var names = new ArrayList<String>();
        for (int index = 0; index < 21; index++) names.add("员工" + index);
        tooManyPreparers.put("preparers", names);
        mvc.perform(post("/api/shipments").contentType(APPLICATION_JSON).content(body(tooManyPreparers, null, null)))
                .andExpect(status().isBadRequest());

        var missingCounty = validForm("测试");
        missingCounty.put("recipientCounty", "");
        mvc.perform(post("/api/shipments").contentType(APPLICATION_JSON).content(body(missingCounty, null, null)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsMissingStatusOrVersionBeforeUpdating() throws Exception {
        login("测试用户", "shipping:edit");
        mvc.perform(put("/api/shipments/17").contentType(APPLICATION_JSON)
                .content(body(validForm("测试"), null, 0L)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(put("/api/shipments/17").contentType(APPLICATION_JSON)
                .content(body(validForm("测试"), "unfinished", null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsInvalidPaginationAndDateRange() throws Exception {
        login("查看人", "shipping:view");
        mvc.perform(get("/api/shipments").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/shipments").param("dateFrom", "2026-09-18").param("dateTo", "2026-09-17"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test
    void passesAllFiltersToPagedRepository() throws Exception {
        login("查看人", "shipping:view");
        when(repository.findAll(any(), any())).thenReturn(org.springframework.data.domain.Page.empty());
        mvc.perform(get("/api/shipments").param("keyword", "收件人").param("platform", "淘宝")
                        .param("incompleteOnly", "true").param("dateFrom", "2026-09-01").param("page", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.records").isArray());
        verify(repository).findAll(eq(new ShipmentQuery("收件人", null, LocalDate.of(2026,9,1), null, "淘宝", true)),
                eq(org.springframework.data.domain.PageRequest.of(1,20)));
    }

    @Test
    void logisticsOrderRequiresBothViewAndOrderPermissions() throws Exception {
        var input = new PlaceAneOrderRequest(0L, "浙江省", "杭州市", "萧山区", "示例路1号", java.math.BigDecimal.TEN,
                java.math.BigDecimal.ONE, 1, "收纳盒", "纸箱", 524, 180, 102, "");
        var json = mapper.writeValueAsString(input);
        login("查看人", "shipping:view");
        mvc.perform(post("/api/shipments/17/logistics-order").contentType(APPLICATION_JSON).content(json)).andExpect(status().isForbidden());
        login("下单人", "shipping:order");
        mvc.perform(post("/api/shipments/17/logistics-order").contentType(APPLICATION_JSON).content(json)).andExpect(status().isForbidden());
        var logistics = context.getBean(AneOrderService.class);
        verifyNoInteractions(logistics);
        login("下单人", "shipping:view", "shipping:order");
        mvc.perform(post("/api/shipments/17/logistics-order").contentType(APPLICATION_JSON).content(json)).andExpect(status().isOk());
        verify(logistics).place(17, input, "employee:42");
    }

    private void login(String displayName, String... permissions) {
        var principal = new ErpPrincipal(42, null, displayName, List.of(), List.of(permissions));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
                java.util.Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList()));
    }

    private Map<String, Object> validForm(String recipient) {
        var form = new LinkedHashMap<String, Object>();
        form.put("platform", "淘宝");
        form.put("shopName", "贝贝鱼淘宝旗舰店");
        form.put("preparers", List.of("小周", "阿杰"));
        form.put("recipientName", recipient);
        form.put("recipientPhone", "13800138000");
        form.put("recipientProvince", "浙江省");
        form.put("recipientCity", "杭州市");
        form.put("recipientCounty", "余杭区");
        form.put("recipientDetailAddress", "示例路1号");
        form.put("preparationContent", "蓝色盒子 2个\n纸箱 1个");
        form.put("remark", "");
        form.put("estimatedFreight", null);
        form.put("orderDraft", Map.of("cargoName", "水族用品", "packType", "纸箱", "weight", 18.5,
                "volume", 0.12, "pieceAmount", 2, "productTypeId", 524, "goodsType", 180,
                "payType", 104, "logisticsRemark", "外箱加固"));
        return form;
    }

    private String body(Map<String, Object> form, String status, Long version) throws Exception {
        var request = new LinkedHashMap<String, Object>();
        request.put("form", form);
        if (status != null) request.put("status", status);
        if (version != null) request.put("version", version);
        return mapper.writeValueAsString(request);
    }
}
