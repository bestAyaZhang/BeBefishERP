package com.bebefish.erp.shipping.api;

import com.bebefish.erp.common.api.GlobalExceptionHandler;
import com.bebefish.erp.common.security.ErpPrincipal;
import com.bebefish.erp.shipping.application.ShipmentService;
import com.bebefish.erp.shipping.application.ShippingFormOptionsService;
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
    private ShippingFormOptionsService optionsService;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean ShipmentRepository repository() { return mock(ShipmentRepository.class); }
        @Bean Clock clock() { return Clock.fixed(Instant.parse("2026-09-24T02:00:00Z"), ZoneId.of("Asia/Shanghai")); }
        @Bean ShipmentEditPolicy shipmentEditPolicy() { return new ShipmentEditPolicy(); }
        @Bean com.bebefish.erp.shipping.application.ShipmentPreparerAssignments assignments() {
            var employees = mock(com.bebefish.erp.shipping.infrastructure.JdbcShippingFormOptionsRepository.class);
            when(employees.findActivePreparers()).thenReturn(List.of(new ShippingFormOptions.PreparerOption(42, "小周"), new ShippingFormOptions.PreparerOption(43, "阿杰")));
            return new com.bebefish.erp.shipping.application.ShipmentPreparerAssignments(employees);
        }
        @Bean ShipmentService service(ShipmentRepository repository, Clock clock, ShipmentEditPolicy policy,
                                     com.bebefish.erp.shipping.application.ShipmentPreparerAssignments assignments) {
            return new ShipmentService(repository, Validation.buildDefaultValidatorFactory().getValidator(), clock, policy, com.bebefish.erp.support.TestShippingSources.resolver(), assignments);
        }
        @Bean ShippingFormOptionsService optionsService() { return mock(ShippingFormOptionsService.class); }
        @Bean ShipmentController controller(ShipmentService service, ShippingFormOptionsService optionsService) {
            return new ShipmentController(service, optionsService);
        }
        @Bean AneOrderService logisticsService() { return mock(AneOrderService.class); }
        @Bean LogisticsOrderController logisticsController(AneOrderService logistics, ShipmentService service) {
            return new LogisticsOrderController(logistics, service);
        }
    }

    @BeforeEach
    void setup() {
        context = new AnnotationConfigApplicationContext(Config.class);
        repository = context.getBean(ShipmentRepository.class);
        optionsService = context.getBean(ShippingFormOptionsService.class);
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(ShipmentController.class), context.getBean(LogisticsOrderController.class))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        when(repository.insert(any())).thenAnswer(call -> {
            Shipment s = call.getArgument(0);
            return new Shipment(17L, s.shipmentNo(), s.content(), 0, s.createdBy(), s.updatedBy(), s.createdAt(), s.updatedAt(),
                    s.logisticsOrderState(), s.preparerEmployeeIds(), s.preparation());
        });
    }

    @AfterEach
    void cleanup() { SecurityContextHolder.clearContext(); context.close(); }

    @Test
    void statusOnlyFeedbackKeepsAnAlreadyRecordedActualWeight() throws Exception {
        login("小周", "shipping:view", "shipping:prepare");
        var form = mapper.convertValue(validForm("测试收件人"), ShipmentFormInput.class);
        var now = java.time.LocalDateTime.of(2026, 9, 24, 9, 0);
        var previous = new Shipment(17L, "FH-17", ShipmentContent.from(LocalDate.of(2026,9,24), form,
                "unfinished", "录入人", "", ""), 3, "employee:1", "employee:1", now, now,
                null, List.of(42L), new PreparationProgress(new java.math.BigDecimal("19.235"), "另一位备货人", 43L, now));
        when(repository.findByIdForUpdate(17)).thenReturn(java.util.Optional.of(previous));
        when(repository.update(any(), eq(3L))).thenReturn(true);
        mvc.perform(patch("/api/shipments/17/preparation").contentType(APPLICATION_JSON)
                        .content("{\"status\":\"completed\",\"version\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.preparation.actualWeight").value(19.235));
    }

    @Test
    void savesAccountAssignmentsAlongsidePreparerNameSnapshots() throws Exception {
        login("录入人", "shipping:create");
        mvc.perform(post("/api/shipments").contentType(APPLICATION_JSON).content(mapper.writeValueAsString(
                        Map.of("form", validForm("收件人"), "preparerEmployeeIds", List.of(42, 43)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.preparerEmployeeIds[0]").value(42))
                .andExpect(jsonPath("$.data.preparerEmployeeIds[1]").value(43));
    }

    @Test
    void preparationPermissionAndEmployeeAssignmentAreBothRequired() throws Exception {
        var form = mapper.convertValue(validForm("测试收件人"), ShipmentFormInput.class);
        var now = java.time.LocalDateTime.of(2026, 9, 24, 9, 0);
        var assigned = new Shipment(17L, "FH-17", ShipmentContent.from(LocalDate.of(2026,9,24), form,
                "unfinished", "录入人", "安能物流", "710001"), 3, "employee:1", "employee:1", now, now,
                "succeeded", List.of(43L), null);
        when(repository.findByIdForUpdate(17)).thenReturn(java.util.Optional.of(assigned));
        String payload = "{\"status\":\"out_of_stock\",\"version\":3}";
        login("小周", "shipping:view", "shipping:edit");
        mvc.perform(patch("/api/shipments/17/preparation").contentType(APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden());
        login("小周", "shipping:prepare");
        mvc.perform(patch("/api/shipments/17/preparation").contentType(APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden());
        login("小周", "shipping:view", "shipping:prepare");
        mvc.perform(patch("/api/shipments/17/preparation").contentType(APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SHIPMENT_PREPARATION_FORBIDDEN"));
        verify(repository, never()).update(any(), anyLong());
        var own = new Shipment(17L, "FH-17", assigned.content(), 3, "employee:1", "employee:1", now, now,
                "succeeded", List.of(42L, 43L), null);
        when(repository.findByIdForUpdate(17)).thenReturn(java.util.Optional.of(own));
        when(repository.update(any(), eq(3L))).thenReturn(true);
        mvc.perform(patch("/api/shipments/17/preparation").contentType(APPLICATION_JSON).content(payload))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content.status").value("out_of_stock"))
                .andExpect(jsonPath("$.data.preparation.actualWeight").doesNotExist());
        mvc.perform(patch("/api/shipments/17/preparation").contentType(APPLICATION_JSON)
                        .content("{\"status\":\"completed\",\"actualWeight\":0,\"version\":3}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/shipments/17/preparation").contentType(APPLICATION_JSON)
                        .content("{\"status\":\"completed\",\"actualWeight\":1.2345,\"version\":3}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/shipments/17/preparation").contentType(APPLICATION_JSON)
                        .content("{\"status\":\"completed\",\"version\":2}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SHIPMENT_VERSION_CONFLICT"));
    }

    @Test
    void administratorCanWritePreparationWithoutChangingCarrierWeightOrState() throws Exception {
        var principal = new ErpPrincipal(42, null, "管理员", List.of("SUPER_ADMIN"), List.of("shipping:view", "shipping:prepare"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
                principal.permissions().stream().map(SimpleGrantedAuthority::new).toList()));
        var form = mapper.convertValue(validForm("测试收件人"), ShipmentFormInput.class);
        var now = java.time.LocalDateTime.of(2026, 9, 24, 9, 0);
        var previous = new Shipment(17L, "FH-17", ShipmentContent.from(LocalDate.of(2026,9,24), form,
                "unfinished", "录入人", "安能物流", "710001"), 3, "employee:1", "employee:1", now, now, "succeeded");
        when(repository.findByIdForUpdate(17)).thenReturn(java.util.Optional.of(previous));
        when(repository.update(any(), eq(3L))).thenReturn(true);
        mvc.perform(patch("/api/shipments/17/preparation").contentType(APPLICATION_JSON)
                        .content("{\"status\":\"completed\",\"actualWeight\":19.235,\"version\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.status").value("completed"))
                .andExpect(jsonPath("$.data.preparation.actualWeight").value(19.235))
                .andExpect(jsonPath("$.data.preparation.updatedBy").value("管理员"))
                .andExpect(jsonPath("$.data.preparation.updatedAt").value("2026-09-24T10:00:00"))
                .andExpect(jsonPath("$.data.content.orderDraft.weight").value(18.5))
                .andExpect(jsonPath("$.data.content.trackingNo").value("710001"))
                .andExpect(jsonPath("$.data.logisticsOrderState").value("succeeded"))
                .andExpect(jsonPath("$.data.version").value(4));
    }

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
        missingShop.remove("shopId");
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
    void viewPermissionCanLoadSummaryAndNarrowFormOptions() throws Exception {
        login("查看人", "shipping:view");
        when(repository.summary(LocalDate.of(2026, 9, 24)))
                .thenReturn(new ShipmentSummary(7, 3, 2, 1, 1));
        when(optionsService.options()).thenReturn(new ShippingFormOptions(
                List.of("贝贝鱼淘宝旗舰店"),
                List.of(new ShippingFormOptions.PreparerOption(9, "小周"))));

        mvc.perform(get("/api/shipments/summary").param("date", "2026-09-24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todayCount").value(7))
                .andExpect(jsonPath("$.data.outOfStockCount").value(1))
                .andExpect(jsonPath("$.data.partiallyShippedCount").value(1));
        mvc.perform(get("/api/shipments/form-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shopNames[0]").value("贝贝鱼淘宝旗舰店"))
                .andExpect(jsonPath("$.data.preparers[0].employeeName").value("小周"));
    }

    @Test
    void summaryAndFormOptionsRequireViewPermission() throws Exception {
        login("录入人", "shipping:create");
        mvc.perform(get("/api/shipments/summary").param("date", "2026-09-24"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/shipments/form-options"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(optionsService);
        verifyNoInteractions(repository);
    }

    @Test
    void logisticsOrderRequiresBothViewAndOrderPermissions() throws Exception {
        var input = new PlaceAneOrderRequest(0L);
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

    @Test
    void cancellationRequiresSeparatePermissionAndValidatedVersion() throws Exception {
        String path = "/api/shipments/17/logistics-order/cancel";
        for (String[] permissions : new String[][] { {"shipping:view", "shipping:order"}, {"shipping:cancel"} }) {
            login("操作人", permissions);
            mvc.perform(post(path).contentType(APPLICATION_JSON).content("{\"version\":3}"))
                    .andExpect(status().isForbidden());
        }
        var logistics = context.getBean(AneOrderService.class);
        verifyNoInteractions(logistics);
        login("操作人", "shipping:view", "shipping:cancel");
        mvc.perform(post(path).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(path).contentType(APPLICATION_JSON).content("{\"version\":3}"))
                .andExpect(status().isOk());
        verify(logistics).cancel(17, new CancelAneOrderRequest(3L), "employee:42");
    }

    private void login(String displayName, String... permissions) {
        var principal = new ErpPrincipal(42, null, displayName, List.of(), List.of(permissions));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
                java.util.Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList()));
    }

    private Map<String, Object> validForm(String recipient) {
        var form = new LinkedHashMap<String, Object>();
        form.put("platformId", 1L); form.put("shopId", 1L); form.put("platform", "淘宝");
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
