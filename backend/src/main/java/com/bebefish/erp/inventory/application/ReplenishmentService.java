package com.bebefish.erp.inventory.application;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.inventory.application.ReplenishmentCalculator.Config;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReplenishmentService {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final int HISTORY_DAYS = 30;
    private static final long SHARED_SCOPE = 0;
    private static final int MAX_BATCH_TARGETS = 100;
    private static final Set<String> STATUSES = Set.of("critical", "warning", "normal", "pending");

    private static final String SKU_FILTER = """
            s.status = 'enabled' and p.status = 'enabled'
            and (:keyword = '' or locate(:keyword, lower(s.sku_code)) > 0
                or locate(:keyword, lower(s.sku_name)) > 0 or locate(:keyword, lower(p.product_name)) > 0)
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public ReplenishmentService(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ListResult list(String status, Long warehouseId, String keyword, int page, int size) {
        if (page < 1 || size < 1 || size > 100 || (warehouseId != null && warehouseId < 1)
                || (status != null && !STATUSES.contains(status))) {
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "补货预警筛选参数无效");
        }
        LocalDate today = LocalDate.now(clock.withZone(SHANGHAI));
        LocalDate windowStart = today.minusDays(HISTORY_DAYS);
        String search = keyword == null ? "" : keyword.strip().toLowerCase(Locale.ROOT);
        List<WarehouseScope> warehouseScopes = jdbc.query("""
                select id, warehouse_name, created_at, independent_replenishment
                from warehouse where status = 'enabled' order by id
                """, (rs, index) -> new WarehouseScope(new Warehouse(rs.getLong("id"), rs.getString("warehouse_name")),
                rs.getObject("created_at", LocalDateTime.class), rs.getBoolean("independent_replenishment")));
        var warehouses = warehouseScopes.stream().map(WarehouseScope::warehouse).toList();
        List<WarehouseScope> sharedScopes = warehouseScopes.stream().filter(scope -> !scope.independent()).toList();
        List<Warehouse> shared = sharedScopes.stream().map(WarehouseScope::warehouse).toList();
        List<WarehouseScope> independent = warehouseScopes.stream().filter(WarehouseScope::independent)
                .filter(scope -> warehouseId == null || warehouseId == scope.warehouse().id()).toList();
        boolean includeShared = !shared.isEmpty()
                && (warehouseId == null || shared.stream().anyMatch(warehouse -> warehouse.id() == warehouseId));
        var results = new Results(status, page, size);
        if (!includeShared && independent.isEmpty()) return results.finish(warehouses);

        var relevantWarehouses = new ArrayList<Long>();
        if (includeShared) shared.forEach(warehouse -> relevantWarehouses.add(warehouse.id()));
        independent.forEach(scope -> relevantWarehouses.add(scope.warehouse().id()));
        var policyScopes = new ArrayList<>(independent.stream().map(scope -> scope.warehouse().id()).toList());
        if (includeShared) policyScopes.add(SHARED_SCOPE);
        var params = Map.of("keyword", search, "windowStart", windowStart, "today", today,
                "warehouseIds", relevantWarehouses, "policyScopes", policyScopes);

        List<Sku> skus = jdbc.query("""
                select s.id, s.sku_code, s.sku_name, p.product_name, s.created_at
                from product_sku s join product_spu p on p.id = s.product_id
                where %s order by s.sku_code, s.id
                """.formatted(SKU_FILTER), params, (rs, index) -> {
            String product = rs.getString("product_name");
            String skuName = rs.getString("sku_name");
            String name = skuName.isBlank() || skuName.equals(product) ? product : product + " · " + skuName;
            return new Sku(rs.getLong("id"), rs.getString("sku_code"), name,
                    rs.getObject("created_at", LocalDateTime.class));
        });
        if (skus.isEmpty()) return results.finish(warehouses);

        Map<ScopeKey, BigDecimal> stock = quantities("""
                select b.sku_id, b.warehouse_id, b.quantity
                from inventory_balance b join product_sku s on s.id = b.sku_id
                join product_spu p on p.id = s.product_id
                where b.warehouse_id in (:warehouseIds) and %s
                """.formatted(SKU_FILTER), params);
        Map<ScopeKey, BigDecimal> demand = quantities("""
                select i.sku_id, o.warehouse_id, sum(i.quantity) as quantity
                from sales_order o join sales_order_item i on i.sales_order_id = o.id
                join product_sku s on s.id = i.sku_id join product_spu p on p.id = s.product_id
                where o.status = 'confirmed' and o.sales_date >= :windowStart and o.sales_date < :today
                and o.warehouse_id in (:warehouseIds) and %s group by i.sku_id, o.warehouse_id
                """.formatted(SKU_FILTER), params);
        Map<ScopeKey, Config> configs = new LinkedHashMap<>();
        jdbc.query("""
                select c.* from inventory_replenishment_policy c
                join product_sku s on s.id = c.sku_id join product_spu p on p.id = s.product_id
                where c.scope_warehouse_id in (:policyScopes) and %s
                """.formatted(SKU_FILTER), params, rs -> {
            configs.put(new ScopeKey(rs.getLong("sku_id"), rs.getLong("scope_warehouse_id")),
                    new Config(rs.getString("mode"), rs.getInt("buffer_days"), rs.getInt("lead_days"),
                            rs.getObject("manual_safety", Long.class), rs.getObject("manual_point", Long.class)));
        });

        // An explicit warehouse + SKU search can prepare a threshold before the first receipt.
        boolean includeUnstocked = warehouseId != null && !search.isEmpty();
        LocalDateTime sharedSince = sharedScopes.stream().map(WarehouseScope::createdAt)
                .min(Comparator.naturalOrder()).orElse(null);
        Map<Long, List<WarehouseScope>> independentBySku = independentRows(independent, stock, demand, configs);
        for (Sku sku : skus) {
            if (includeShared) {
                LocalDateTime createdAt = laterOf(sku.createdAt(), sharedSince);
                results.add(row(sku, null, shared, total(stock, sku.id(), shared), total(demand, sku.id(), shared),
                        configs.get(new ScopeKey(sku.id(), SHARED_SCOPE)), createdAt, windowStart, today));
            }
            List<WarehouseScope> skuWarehouses = includeUnstocked ? independent
                    : independentBySku.getOrDefault(sku.id(), List.of());
            for (WarehouseScope scope : skuWarehouses) {
                Warehouse warehouse = scope.warehouse();
                var key = new ScopeKey(sku.id(), warehouse.id());
                LocalDateTime createdAt = laterOf(sku.createdAt(), scope.createdAt());
                results.add(row(sku, warehouse.id(), List.of(warehouse), stock.getOrDefault(key, BigDecimal.ZERO),
                        demand.getOrDefault(key, BigDecimal.ZERO), configs.get(key), createdAt, windowStart, today));
            }
        }
        return results.finish(warehouses);
    }

    @SafeVarargs
    private final Map<Long, List<WarehouseScope>> independentRows(List<WarehouseScope> scopes, Map<ScopeKey, ?>... data) {
        Map<Long, WarehouseScope> scopesById = new HashMap<>();
        scopes.forEach(scope -> scopesById.put(scope.warehouse().id(), scope));
        var found = new HashSet<ScopeKey>();
        Map<Long, List<WarehouseScope>> bySku = new HashMap<>();
        Stream.of(data).flatMap(map -> map.keySet().stream()).forEach(key -> {
            WarehouseScope scope = scopesById.get(key.warehouseId());
            if (scope != null && found.add(key)) {
                bySku.computeIfAbsent(key.skuId(), ignored -> new ArrayList<>()).add(scope);
            }
        });
        bySku.values().forEach(list -> list.sort(Comparator.comparingLong(scope -> scope.warehouse().id())));
        return bySku;
    }

    @Transactional
    public void save(long skuId, Long warehouseId, Config config) {
        saveTargets(List.of(new Target(skuId, warehouseId)), config);
    }

    @Transactional
    public void saveBatch(List<Target> targets, Config config) {
        saveTargets(targets, config);
    }

    private void saveTargets(List<Target> targets, Config config) {
        ReplenishmentCalculator.validate(config);
        if (targets == null || targets.isEmpty() || targets.size() > MAX_BATCH_TARGETS) {
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "请选择 1 至 100 条补货记录");
        }
        Set<Target> unique = new HashSet<>();
        for (Target target : targets) {
            if (target == null || target.skuId() == null || target.skuId() < 1
                    || (target.warehouseId() != null && target.warehouseId() < 1) || !unique.add(target)) {
                throw new BusinessException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "补货记录无效或重复");
            }
        }
        var ordered = targets.stream().sorted(Comparator.comparingLong(Target::skuId)
                .thenComparingLong(Target::scope)).toList();

        // Both save paths lock products, SKUs and warehouses in this order before writing policies.
        lockSkus(ordered.stream().map(Target::skuId).distinct().toList());
        lockWarehouseScopes(ordered);
        var batch = ordered.stream().map(target -> new MapSqlParameterSource()
                .addValue("skuId", target.skuId()).addValue("scope", target.scope())
                .addValue("mode", config.mode()).addValue("bufferDays", config.bufferDays())
                .addValue("leadDays", config.leadDays()).addValue("manualSafety", config.manualSafety())
                .addValue("manualPoint", config.manualPoint())).toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate("""
                insert into inventory_replenishment_policy
                    (sku_id, scope_warehouse_id, mode, buffer_days, lead_days, manual_safety, manual_point)
                values (:skuId, :scope, :mode, :bufferDays, :leadDays, :manualSafety, :manualPoint)
                on duplicate key update mode = :mode, buffer_days = :bufferDays, lead_days = :leadDays,
                    manual_safety = :manualSafety, manual_point = :manualPoint
                """, batch);
    }

    private void lockSkus(List<Long> skuIds) {
        var skuParams = Map.of("ids", skuIds);
        var productIds = jdbc.queryForList("""
                select distinct product_id from product_sku where id in (:ids) order by product_id
                """, skuParams, Long.class);
        if (productIds.isEmpty()) throw skuNotFound();
        var enabledProducts = Set.copyOf(jdbc.queryForList("""
                select id from product_spu where id in (:ids) and status = 'enabled' order by id for update
                """, Map.of("ids", productIds), Long.class));
        var skuProducts = jdbc.queryForList("""
                select product_id from product_sku where id in (:ids) and status = 'enabled' order by id for update
                """, skuParams, Long.class);
        if (skuProducts.size() != skuIds.size() || !enabledProducts.containsAll(skuProducts)) {
            throw skuNotFound();
        }
    }

    private void lockWarehouseScopes(List<Target> targets) {
        var warehouseIds = new TreeSet<Long>();
        boolean needsShared = false;
        for (Target target : targets) {
            if (target.warehouseId() == null) {
                needsShared = true;
            } else {
                warehouseIds.add(target.warehouseId());
            }
        }
        Long sharedId = null;
        if (needsShared) {
            var shared = jdbc.queryForList("""
                    select id from warehouse where status = 'enabled' and not independent_replenishment
                    order by id limit 1
                    """, Map.of(), Long.class);
            if (shared.isEmpty()) throw scopeChanged();
            sharedId = shared.getFirst();
            warehouseIds.add(sharedId);
        }
        Map<Long, Boolean> independentModes = new HashMap<>();
        jdbc.query("""
                select id, independent_replenishment from warehouse
                where id in (:ids) and status = 'enabled' order by id for update
                """, Map.of("ids", warehouseIds), rs -> {
            independentModes.put(rs.getLong("id"), rs.getBoolean("independent_replenishment"));
        });
        if (sharedId != null && !Boolean.FALSE.equals(independentModes.get(sharedId))) throw scopeChanged();
        for (Target target : targets) {
            if (target.warehouseId() != null && !Boolean.TRUE.equals(independentModes.get(target.warehouseId()))) {
                throw scopeChanged();
            }
        }
    }

    private static BusinessException skuNotFound() {
        return new BusinessException("SKU_NOT_FOUND", HttpStatus.NOT_FOUND, "SKU 不存在或已停用");
    }

    private static BusinessException scopeChanged() {
        return new BusinessException("REPLENISHMENT_SCOPE_CHANGED", HttpStatus.CONFLICT, "补货范围已变更，请刷新列表后重试");
    }

    private Map<ScopeKey, BigDecimal> quantities(String sql, Map<String, ?> params) {
        Map<ScopeKey, BigDecimal> values = new LinkedHashMap<>();
        jdbc.query(sql, params, rs -> {
            values.put(new ScopeKey(rs.getLong("sku_id"), rs.getLong("warehouse_id")), rs.getBigDecimal("quantity"));
        });
        return values;
    }

    private BigDecimal total(Map<ScopeKey, BigDecimal> quantities, long skuId, List<Warehouse> warehouses) {
        return warehouses.stream().map(warehouse -> quantities.getOrDefault(new ScopeKey(skuId, warehouse.id()), BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static LocalDateTime laterOf(LocalDateTime first, LocalDateTime second) {
        return first.isAfter(second) ? first : second;
    }

    private Row row(Sku sku, Long warehouseId, List<Warehouse> warehouses, BigDecimal stock, BigDecimal demand,
                    Config config, LocalDateTime createdAt, LocalDate start, LocalDate today) {
        LocalDate firstCompleteDay = createdAt.toLocalDate();
        if (!createdAt.toLocalTime().equals(LocalTime.MIDNIGHT)) firstCompleteDay = firstCompleteDay.plusDays(1);
        if (firstCompleteDay.isBefore(start)) firstCompleteDay = start;
        int historyDays = (int) Math.max(0, ChronoUnit.DAYS.between(firstCompleteDay, today));
        Config effectiveConfig = config == null ? ReplenishmentCalculator.DEFAULT_CONFIG : config;
        var result = ReplenishmentCalculator.evaluate(stock, historyDays, demand, effectiveConfig);
        return new Row(sku.id(), sku.code(), sku.name(), warehouses, warehouseId, stock, historyDays, demand,
                start, today.minusDays(1), effectiveConfig, result.average(), result.safety(), result.point(),
                result.source(), result.status());
    }

    public record Warehouse(long id, String name) {}

    public record Target(Long skuId, Long warehouseId) {
        private long scope() {
            return warehouseId == null ? SHARED_SCOPE : warehouseId;
        }
    }

    public record Row(long skuId, String sku, String name, List<Warehouse> warehouses, Long warehouseId,
                      BigDecimal stock, int historyDays, BigDecimal demandTotal, LocalDate windowStart, LocalDate windowEnd,
                      Config config, BigDecimal average, Long safety, Long point, String source, String status) {
        @JsonProperty public String stockExact() { return stock.toPlainString(); }
        @JsonProperty public String demandTotalExact() { return demandTotal.toPlainString(); }
    }

    public record ListResult(List<Row> items, long total, int page, int size, List<Warehouse> warehouses,
                             Map<String, Long> counts) {}

    private record Sku(long id, String code, String name, LocalDateTime createdAt) {}
    private record WarehouseScope(Warehouse warehouse, LocalDateTime createdAt, boolean independent) {}
    private record ScopeKey(long skuId, long warehouseId) {}

    private static class Results {
        private final String status;
        private final int page;
        private final int size;
        private final long offset;
        private final List<Row> items = new ArrayList<>();
        private final Map<String, Long> counts = new LinkedHashMap<>();
        private long total;

        Results(String status, int page, int size) {
            this.status = status;
            this.page = page;
            this.size = size;
            this.offset = (page - 1L) * size;
            STATUSES.forEach(key -> counts.put(key, 0L));
        }

        void add(Row row) {
            counts.merge(row.status(), 1L, Long::sum);
            if (status == null || status.equals(row.status())) {
                if (total >= offset && items.size() < size) items.add(row);
                total++;
            }
        }

        ListResult finish(List<Warehouse> warehouses) {
            return new ListResult(items, total, page, size, warehouses, counts);
        }
    }
}
