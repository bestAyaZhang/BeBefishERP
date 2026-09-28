package com.bebefish.erp.inventory.application;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.inventory.application.StocktakeViews.Count;
import com.bebefish.erp.inventory.application.StocktakeViews.Details;
import com.bebefish.erp.inventory.application.StocktakeViews.Item;
import com.bebefish.erp.inventory.application.StocktakeViews.ListResult;
import com.bebefish.erp.inventory.application.StocktakeViews.Summary;
import com.bebefish.erp.inventory.application.StocktakeViews.Task;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StocktakeService {
    private static final DateTimeFormatter TASK_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private final NamedParameterJdbcTemplate jdbc;
    private final InventoryService inventoryService;
    private final WarehouseInventoryLayoutQueryService layoutQuery;
    private final ObjectMapper objectMapper;

    public StocktakeService(
            NamedParameterJdbcTemplate jdbc,
            InventoryService inventoryService,
            WarehouseInventoryLayoutQueryService layoutQuery,
            ObjectMapper objectMapper
    ) {
        this.jdbc = jdbc;
        this.inventoryService = inventoryService;
        this.layoutQuery = layoutQuery;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ListResult list(Long warehouseId, String status, String keyword) {
        var params = new MapSqlParameterSource();
        var conditions = new java.util.ArrayList<String>();
        if (warehouseId != null) {
            conditions.add("warehouse_id = :warehouseId");
            params.addValue("warehouseId", warehouseId);
        }
        if (status != null && !status.isBlank()) {
            conditions.add("status = :status");
            params.addValue("status", status.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            conditions.add("(task_no like :keyword or warehouse_name like :keyword or assignee_name like :keyword)");
            params.addValue("keyword", "%" + keyword.trim() + "%");
        }
        var where = conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
        var tasks = jdbc.query("""
                select id, task_no, warehouse_id, warehouse_name, scope_label, assignee_name,
                       counted_items, total_items, difference_items, status, created_at
                from inventory_stocktake_task
                """ + where + " order by created_at desc, id desc", params, (rs, rowNum) -> task(rs));
        var counters = jdbc.queryForMap("""
                select
                    sum(case when status='in_progress' then 1 else 0 end) as in_progress,
                    sum(case when status='awaiting_recount' then 1 else 0 end) as awaiting_recount,
                    sum(case when status='awaiting_approval' then 1 else 0 end) as awaiting_approval,
                    sum(case when status='completed' and created_at >= :monthStart then 1 else 0 end) as completed_month,
                    coalesce(sum(difference_items), 0) as discrepancy_items
                from inventory_stocktake_task
                """, new MapSqlParameterSource("monthStart", LocalDate.now().withDayOfMonth(1).atStartOfDay()));
        var itemAccuracy = jdbc.queryForMap("""
                select count(*) as total_count,
                       sum(case when difference_quantity=0 then 1 else 0 end) as matched_count
                from inventory_stocktake_item
                where first_count_quantity is not null
                """, Map.of());
        var total = number(itemAccuracy.get("total_count"));
        var matched = number(itemAccuracy.get("matched_count"));
        var accuracy = total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(matched)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
        return new ListResult(new Summary(
                number(counters.get("in_progress")),
                number(counters.get("awaiting_recount")),
                number(counters.get("awaiting_approval")),
                number(counters.get("completed_month")),
                number(counters.get("discrepancy_items")),
                accuracy
        ), tasks);
    }

    @Transactional(readOnly = true)
    public Details get(long id) {
        var task = jdbc.query("""
                select id, task_no, warehouse_id, warehouse_name, scope_label, assignee_name,
                       counted_items, total_items, difference_items, status, created_at, blind_count
                from inventory_stocktake_task where id=:id
                """, Map.of("id", id), (rs, rowNum) -> new Details(
                rs.getLong("id"), rs.getString("task_no"), rs.getLong("warehouse_id"),
                rs.getString("warehouse_name"), rs.getString("scope_label"), rs.getString("assignee_name"),
                rs.getInt("counted_items"), rs.getInt("total_items"), rs.getInt("difference_items"),
                rs.getString("status"), rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getBoolean("blind_count"), List.of()
        )).stream().findFirst().orElseThrow(() -> notFound("盘点任务不存在"));
        var layoutLabels = loadLayoutLabels(task.warehouseId());
        var items = jdbc.query("""
                select id, zone_name, pallet_id, pallet_label, sku_id, sku_code, product_name,
                       sku_name, specification, units_per_case, book_quantity, first_count_quantity,
                       recount_quantity, difference_quantity, status
                from inventory_stocktake_item where task_id=:id
                order by coalesce(zone_name, ''), pallet_id, sku_id
                """, Map.of("id", id), (rs, rowNum) -> new Item(
                rs.getLong("id"), rs.getString("zone_name"), rs.getString("pallet_id"),
                rs.getString("pallet_label"), rs.getLong("sku_id"), rs.getString("sku_code"),
                rs.getString("product_name"), rs.getString("sku_name"), rs.getString("specification"),
                rs.getObject("units_per_case", Integer.class), rs.getBigDecimal("book_quantity"),
                rs.getBigDecimal("first_count_quantity"), rs.getBigDecimal("recount_quantity"),
                rs.getBigDecimal("difference_quantity"), rs.getString("status")
        )).stream().map(item -> new Item(
                item.id(), zoneLabel(layoutLabels, item.zoneName()), item.palletId(),
                palletLabel(layoutLabels, item.palletId(), item.palletLabel()), item.skuId(),
                item.skuCode(), item.productName(), item.skuName(), item.specification(),
                item.unitsPerCase(), item.bookQuantity(), item.firstCountQuantity(),
                item.recountQuantity(), item.difference(), item.status()
        )).toList();
        return new Details(
                task.id(), task.taskNo(), task.warehouseId(), task.warehouseName(), task.scopeLabel(),
                task.assigneeName(), task.countedItems(), task.totalItems(), task.differenceItems(),
                task.status(), task.createdAt(), task.blindCount(), items
        );
    }

    @Transactional
    public Details create(
            long warehouseId,
            String assigneeName,
            boolean blindCount,
            List<String> palletIds,
            String operator
    ) {
        if (warehouseId <= 0) throw validation("仓库不能为空");
        if (assigneeName == null || assigneeName.isBlank()) throw validation("负责人不能为空");
        var warehouseName = jdbc.query("""
                select warehouse_name from warehouse where id=:id and status='enabled'
                """, Map.of("id", warehouseId), (rs, rowNum) -> rs.getString(1))
                .stream().findFirst().orElseThrow(() -> validation("仓库不存在或已停用"));
        var planned = jdbc.queryForObject("""
                select count(*) from warehouse_layout
                where warehouse_id=:id and layout_json like '%\"completed\":true%'
                """, Map.of("id", warehouseId), Integer.class);
        if (planned == null || planned == 0) throw validation("该仓库尚未完成规划，无法创建盘点任务");
        var layoutLabels = loadLayoutLabels(warehouseId);
        var selectedPalletIds = palletIds == null ? List.<String>of() : palletIds.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        for (var palletId : selectedPalletIds) {
            if (!layoutLabels.piles().containsKey(palletId)) {
                throw validation("所选货物堆不存在或不属于当前仓库");
            }
        }
        var selectedScope = !selectedPalletIds.isEmpty();
        var snapshot = layoutQuery.get(warehouseId).allocations().stream()
                .filter(row -> row.units().signum() > 0)
                .filter(row -> !selectedScope || selectedPalletIds.contains(row.palletId()))
                .map(row -> new Snapshot(
                        row.zoneId(), row.palletId(), row.skuId(),
                        fallback(row.skuCode(), "SKU-" + row.skuId()),
                        fallback(row.productName(), "未匹配商品"),
                        fallback(row.skuName(), "SKU " + row.skuId()),
                        row.specification(), row.unitsPerCase(), row.units()
                ))
                .toList();
        if (snapshot.isEmpty()) {
            throw validation(selectedScope ? "所选货物堆暂无可盘点库存" : "该仓库暂无可盘点库存");
        }
        if (selectedScope && snapshot.stream().map(Snapshot::palletId).distinct().count() != selectedPalletIds.size()) {
            throw validation("所选货物堆中存在无库存货物堆");
        }
        var zoneCount = snapshot.stream().map(Snapshot::zoneName).filter(value -> value != null && !value.isBlank()).distinct().count();
        var pileCount = snapshot.stream().map(Snapshot::palletId).distinct().count();
        var scope = selectedScope
                ? "指定货物堆 · " + pileCount + "堆"
                : zoneCount == 0
                    ? "全仓 · " + pileCount + "个货物堆"
                    : "全仓 · " + zoneCount + "区" + pileCount + "堆";
        var now = LocalDateTime.now();
        var keyHolder = new GeneratedKeyHolder();
        var temporaryTaskNo = "PENDING-" + java.util.UUID.randomUUID();
        jdbc.update("""
                insert into inventory_stocktake_task (
                    task_no, warehouse_id, warehouse_name, scope_label, assignee_name, blind_count,
                    status, total_items, counted_items, difference_items, created_by, created_at, updated_at
                ) values (:temporaryTaskNo, :warehouseId, :warehouseName, :scopeLabel, :assigneeName, :blindCount,
                          'not_started', :totalItems, 0, 0, :createdBy, :createdAt, :updatedAt)
                """, new MapSqlParameterSource()
                .addValue("temporaryTaskNo", temporaryTaskNo)
                .addValue("warehouseId", warehouseId)
                .addValue("warehouseName", warehouseName)
                .addValue("scopeLabel", scope)
                .addValue("assigneeName", assigneeName.trim())
                .addValue("blindCount", blindCount)
                .addValue("totalItems", snapshot.size())
                .addValue("createdBy", operator)
                .addValue("createdAt", now)
                .addValue("updatedAt", now), keyHolder, new String[]{"id"});
        var id = keyHolder.getKey().longValue();
        var taskNo = "PD" + now.toLocalDate().format(TASK_DATE) + String.format("%05d", id);
        jdbc.update("update inventory_stocktake_task set task_no=:taskNo where id=:id", Map.of("taskNo", taskNo, "id", id));
        for (var row : snapshot) {
            jdbc.update("""
                    insert into inventory_stocktake_item (
                        task_id, zone_name, pallet_id, pallet_label, sku_id, sku_code, product_name,
                        sku_name, specification, units_per_case, book_quantity, status, created_at, updated_at
                    ) values (:taskId, :zoneName, :palletId, :palletLabel, :skuId, :skuCode, :productName,
                              :skuName, :specification, :unitsPerCase, :bookQuantity, 'uncounted', :createdAt, :updatedAt)
                    """, new MapSqlParameterSource()
                    .addValue("taskId", id)
                    .addValue("zoneName", zoneLabel(layoutLabels, row.zoneName()), Types.VARCHAR)
                    .addValue("palletId", row.palletId())
                    .addValue("palletLabel", palletLabel(layoutLabels, row.palletId(), row.palletId()))
                    .addValue("skuId", row.skuId())
                    .addValue("skuCode", row.skuCode())
                    .addValue("productName", row.productName())
                    .addValue("skuName", row.skuName())
                    .addValue("specification", row.specification(), Types.VARCHAR)
                    .addValue("unitsPerCase", row.unitsPerCase(), Types.INTEGER)
                    .addValue("bookQuantity", row.quantity())
                    .addValue("createdAt", now)
                    .addValue("updatedAt", now));
        }
        return get(id);
    }

    @Transactional
    public Details saveDraft(long id, List<Count> counts) {
        applyCounts(id, counts, false);
        return get(id);
    }

    @Transactional
    public Details submitInitial(long id, List<Count> counts) {
        applyCounts(id, counts, true);
        return get(id);
    }

    @Transactional
    public Details submitRecount(long id, List<Count> counts) {
        var status = lockStatus(id);
        if (!status.equals("awaiting_recount")) throw validation("当前状态不可提交复盘");
        var differenceItemIds = new HashSet<>(jdbc.query("""
                select id from inventory_stocktake_item where task_id=:id and difference_quantity<>0
                """, Map.of("id", id), (rs, rowNum) -> rs.getLong(1)));
        if (counts == null || counts.size() != differenceItemIds.size()) throw validation("请完成全部差异项复盘");
        var seen = new HashSet<Long>();
        for (var count : counts) {
            validateCount(count, seen);
            if (!differenceItemIds.contains(count.itemId())) throw validation("只能提交当前任务的差异项");
            jdbc.update("""
                    update inventory_stocktake_item
                    set recount_quantity=:quantity,
                        difference_quantity=:quantity-book_quantity,
                        status=case when :quantity=book_quantity then 'matched' else 'recount_required' end,
                        updated_at=:updatedAt
                    where id=:itemId and task_id=:taskId
                    """, new MapSqlParameterSource()
                    .addValue("quantity", count.quantity())
                    .addValue("updatedAt", LocalDateTime.now())
                    .addValue("itemId", count.itemId())
                    .addValue("taskId", id));
        }
        var differences = jdbc.queryForObject("""
                select count(*) from inventory_stocktake_item
                where task_id=:id and coalesce(recount_quantity, first_count_quantity)<>book_quantity
                """, Map.of("id", id), Integer.class);
        jdbc.update("""
                update inventory_stocktake_task
                set status='awaiting_approval', difference_items=:differences, updated_at=:updatedAt
                where id=:id
                """, Map.of("differences", differences, "updatedAt", LocalDateTime.now(), "id", id));
        return get(id);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Details approve(long id, String operator) {
        var status = lockStatus(id);
        if (!status.equals("awaiting_approval")) throw validation("当前状态不可审批");
        var task = get(id);
        lockWarehouseInventory(task.warehouseId());
        var selectedScope = !task.scopeLabel().startsWith("全仓");
        var selectedPalletIds = task.items().stream().map(Item::palletId).collect(java.util.stream.Collectors.toSet());
        var current = new LinkedHashMap<Position, BigDecimal>();
        for (var allocation : layoutQuery.get(task.warehouseId()).allocations()) {
            if (allocation.units().signum() <= 0) continue;
            if (!selectedScope || selectedPalletIds.contains(allocation.palletId())) {
                current.put(new Position(allocation.palletId(), allocation.skuId()), allocation.units());
            }
        }
        var expected = new LinkedHashMap<Position, BigDecimal>();
        for (var item : task.items()) {
            expected.put(new Position(item.palletId(), item.skuId()), item.bookQuantity());
        }
        if (!current.equals(expected)) throw snapshotStale();
        var bookBySku = new LinkedHashMap<Long, BigDecimal>();
        var finalBySku = new LinkedHashMap<Long, BigDecimal>();
        for (var item : task.items()) {
            var finalQuantity = item.recountQuantity() != null ? item.recountQuantity() : item.firstCountQuantity();
            if (finalQuantity == null) throw validation("盘点结果不完整，无法审批");
            bookBySku.merge(item.skuId(), item.bookQuantity(), BigDecimal::add);
            finalBySku.merge(item.skuId(), finalQuantity, BigDecimal::add);
        }
        for (var item : task.items()) {
            var finalQuantity = item.recountQuantity() != null ? item.recountQuantity() : item.firstCountQuantity();
            if ("UNALLOCATED".equals(item.palletId())) continue;
            var updated = jdbc.update("""
                    update inventory_location_balance
                    set quantity=:quantity, version_no=version_no+1, updated_at=:updatedAt
                    where warehouse_id=:warehouseId and pallet_id=:palletId and sku_id=:skuId
                      and quantity=:bookQuantity
                    """, new MapSqlParameterSource()
                    .addValue("quantity", finalQuantity)
                    .addValue("bookQuantity", item.bookQuantity())
                    .addValue("updatedAt", LocalDateTime.now())
                    .addValue("warehouseId", task.warehouseId())
                    .addValue("palletId", item.palletId())
                    .addValue("skuId", item.skuId()));
            if (updated != 1) throw snapshotStale();
        }
        var changes = finalBySku.entrySet().stream()
                .map(entry -> new InventoryChange(entry.getKey(), entry.getValue().subtract(bookBySku.get(entry.getKey()))))
                .filter(change -> change.quantity().signum() != 0)
                .toList();
        if (!changes.isEmpty()) {
            inventoryService.adjust(
                    task.warehouseId(), changes, new InventorySource("stocktake", id, task.taskNo()), operator
            );
        }
        jdbc.update("""
                update inventory_stocktake_item set status='approved', updated_at=:updatedAt where task_id=:id
                """, Map.of("updatedAt", LocalDateTime.now(), "id", id));
        jdbc.update("""
                update inventory_stocktake_task set status='completed', updated_at=:updatedAt where id=:id
                """, Map.of("updatedAt", LocalDateTime.now(), "id", id));
        return get(id);
    }

    private void lockWarehouseInventory(long warehouseId) {
        var parameters = Map.of("warehouseId", warehouseId);
        jdbc.query("select id from warehouse where id=:warehouseId for update", parameters,
                (rs, rowNum) -> rs.getLong(1));
        jdbc.query("""
                select sku_id from inventory_balance where warehouse_id=:warehouseId
                order by sku_id for update
                """, parameters, (rs, rowNum) -> rs.getLong(1));
        jdbc.query("""
                select pallet_id from inventory_location_balance where warehouse_id=:warehouseId
                order by sku_id, pallet_id for update
                """, parameters, (rs, rowNum) -> rs.getString(1));
    }

    private void applyCounts(long id, List<Count> counts, boolean submit) {
        var currentStatus = lockStatus(id);
        if (!currentStatus.equals("not_started") && !currentStatus.equals("in_progress")) {
            throw validation("当前状态不可修改初盘数据");
        }
        if (counts == null) throw validation("盘点数量不能为空");
        var seen = new HashSet<Long>();
        for (var count : counts) {
            validateCount(count, seen);
            var updated = jdbc.update("""
                    update inventory_stocktake_item
                    set first_count_quantity=:quantity, difference_quantity=null, status='counted', updated_at=:updatedAt
                    where id=:itemId and task_id=:taskId
                    """, new MapSqlParameterSource()
                    .addValue("quantity", count.quantity())
                    .addValue("updatedAt", LocalDateTime.now())
                    .addValue("itemId", count.itemId())
                    .addValue("taskId", id));
            if (updated != 1) throw validation("盘点明细不存在或不属于当前任务");
        }
        var counted = jdbc.queryForObject("""
                select count(*) from inventory_stocktake_item where task_id=:id and first_count_quantity is not null
                """, Map.of("id", id), Integer.class);
        var total = jdbc.queryForObject("select total_items from inventory_stocktake_task where id=:id", Map.of("id", id), Integer.class);
        if (submit && !java.util.Objects.equals(counted, total)) throw validation("请完成全部盘点明细后再提交");
        if (submit) {
            jdbc.update("""
                    update inventory_stocktake_item
                    set difference_quantity=first_count_quantity-book_quantity,
                        status=case when first_count_quantity=book_quantity then 'matched' else 'difference' end,
                        updated_at=:updatedAt
                    where task_id=:id
                    """, Map.of("updatedAt", LocalDateTime.now(), "id", id));
            var differences = jdbc.queryForObject("""
                    select count(*) from inventory_stocktake_item where task_id=:id and difference_quantity<>0
                    """, Map.of("id", id), Integer.class);
            jdbc.update("""
                    update inventory_stocktake_task
                    set counted_items=:counted, difference_items=:differences,
                        status=case when :differences>0 then 'awaiting_recount' else 'awaiting_approval' end,
                        submitted_at=:updatedAt, updated_at=:updatedAt
                    where id=:id
                    """, Map.of("counted", counted, "differences", differences, "updatedAt", LocalDateTime.now(), "id", id));
        } else {
            jdbc.update("""
                    update inventory_stocktake_task
                    set counted_items=:counted,
                        status=case when :counted>0 then 'in_progress' else 'not_started' end,
                        updated_at=:updatedAt
                    where id=:id
                    """, Map.of("counted", counted, "updatedAt", LocalDateTime.now(), "id", id));
        }
    }

    private String lockStatus(long id) {
        return jdbc.query("select status from inventory_stocktake_task where id=:id for update",
                        Map.of("id", id), (rs, rowNum) -> rs.getString(1)).stream().findFirst()
                .orElseThrow(() -> notFound("盘点任务不存在"));
    }

    private void validateCount(Count count, HashSet<Long> seen) {
        if (count == null || count.itemId() <= 0 || count.quantity() == null || count.quantity().signum() < 0
                || count.quantity().stripTrailingZeros().scale() > 0 || !seen.add(count.itemId())) {
            throw validation("盘点数量必须是大于等于 0 的整数，且明细不可重复");
        }
    }

    private Task task(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Task(
                rs.getLong("id"), rs.getString("task_no"), rs.getLong("warehouse_id"),
                rs.getString("warehouse_name"), rs.getString("scope_label"), rs.getString("assignee_name"),
                rs.getInt("counted_items"), rs.getInt("total_items"), rs.getInt("difference_items"),
                rs.getString("status"), rs.getTimestamp("created_at").toLocalDateTime()
        );
    }

    private int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private LayoutLabels loadLayoutLabels(long warehouseId) {
        var layouts = jdbc.query(
                "select layout_json from warehouse_layout where warehouse_id=:warehouseId",
                Map.of("warehouseId", warehouseId),
                (rs, rowNum) -> rs.getString("layout_json")
        );
        if (layouts.isEmpty()) return LayoutLabels.empty();
        try {
            var document = objectMapper.readTree(layouts.getFirst());
            var zones = new LinkedHashMap<String, String>();
            for (var zone : document.path("structure").path("zones")) {
                if (zone.path("id").isTextual()) {
                    zones.put(zone.path("id").textValue(), firstText(zone, "label", "name", "code"));
                }
            }
            var piles = new LinkedHashMap<String, String>();
            for (var pile : document.path("palletGroups")) {
                if (pile.path("id").isTextual()) {
                    piles.put(pile.path("id").textValue(), firstText(pile, "code", "name"));
                }
            }
            return new LayoutLabels(zones, piles);
        } catch (JsonProcessingException ignored) {
            return LayoutLabels.empty();
        }
    }

    private String firstText(com.fasterxml.jackson.databind.JsonNode node, String... fields) {
        for (var field : fields) {
            var value = node.path(field);
            if (value.isTextual() && !value.textValue().isBlank()) return value.textValue();
        }
        return node.path("id").asText();
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String zoneLabel(LayoutLabels labels, String zoneId) {
        if (zoneId == null || zoneId.isBlank()) return "全仓";
        return labels.zones().getOrDefault(zoneId, zoneId);
    }

    private String palletLabel(LayoutLabels labels, String palletId, String fallback) {
        if ("UNALLOCATED".equals(palletId)) return "待分配";
        return labels.piles().getOrDefault(palletId, fallback);
    }

    private BusinessException validation(String message) {
        return new BusinessException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, message);
    }

    private BusinessException notFound(String message) {
        return new BusinessException("NOT_FOUND", HttpStatus.NOT_FOUND, message);
    }

    private BusinessException snapshotStale() {
        return new BusinessException("STOCKTAKE_SNAPSHOT_STALE", HttpStatus.CONFLICT,
                "盘点期间库存已发生变化，请重新创建盘点任务");
    }

    private record Snapshot(
            String zoneName,
            String palletId,
            long skuId,
            String skuCode,
            String productName,
            String skuName,
            String specification,
            Integer unitsPerCase,
            BigDecimal quantity
    ) {
    }

    private record Position(String palletId, long skuId) {
    }

    private record LayoutLabels(Map<String, String> zones, Map<String, String> piles) {
        private static LayoutLabels empty() {
            return new LayoutLabels(Map.of(), Map.of());
        }
    }
}
