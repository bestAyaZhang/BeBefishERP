<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, shallowRef, watch } from 'vue';
import {
  ChevronDown, ChevronLeft, ChevronRight, CircleAlert, CircleCheck, CircleHelp,
  Search, Settings2, TriangleAlert
} from 'lucide-vue-next';
import { message } from '../../../components/feedback/message';
import { currentUser } from '../../../services/authSession';
import type { ReplenishmentConfig, ReplenishmentPage, ReplenishmentRow, ReplenishmentStatus } from './replenishmentModel';
import { replenishmentService } from './replenishmentService';
import ReplenishmentSettings from './ReplenishmentSettings.vue';

const props = withDefaults(defineProps<{ active?: boolean }>(), { active: true });

const PAGE_SIZES = [20, 50, 100] as const;
const MAX_BATCH_SIZE = 100;
const SEARCH_DELAY_MS = 250;
type PageSize = (typeof PAGE_SIZES)[number];
type BatchTarget = Pick<ReplenishmentRow, 'skuId' | 'warehouseId'>;
const statuses = {
  critical: { label: '紧急补货', icon: CircleAlert, hint: '库存 ≤ 安全库存' },
  warning: { label: '建议补货', icon: TriangleAlert, hint: '库存 ≤ 补货点' },
  normal: { label: '正常', icon: CircleCheck, hint: '库存 > 补货点' },
  pending: { label: '待设置', icon: CircleHelp, hint: '尚无生效阈值' }
};
const sources = { auto: '自动计算', manual: '手工设置', fallback: '手工兜底', pending: '' };
const numberFormat = new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 4 });
const format = (value: number | null) => value == null ? '—' : numberFormat.format(value);

const root = ref<HTMLElement | null>(null);
const searchField = ref<HTMLInputElement | null>(null);
const batchButton = ref<HTMLButtonElement | null>(null);
const query = ref('');
const warehouseId = ref<number | ''>('');
const status = ref<ReplenishmentStatus | ''>('');
const page = ref(1);
const pageSize = ref<PageSize>(20);
const response = shallowRef<ReplenishmentPage | null>(null);
const loading = ref(true);
const loadingDifferentPage = ref(true);
const listError = ref('');
const selected = shallowRef(new Map<string, ReplenishmentRow>());
const selectionError = ref('');
const settings = shallowRef<{ row: ReplenishmentRow; targets?: BatchTarget[] } | null>(null);
const saving = ref(false);
const saveError = ref('');
let latestRequest = 0;
let searchTimer: ReturnType<typeof setTimeout> | undefined;
let disposed = false;

const canEdit = computed(() => currentUser.value?.permissions.includes('inventory:edit') === true);
const hasFilters = computed(() => Boolean(query.value.trim() || warehouseId.value || status.value));
const results = computed(() => loadingDifferentPage.value ? null : response.value);
const rows = computed(() => results.value?.items ?? []);
const counts = computed(() => results.value?.counts);
const totalPages = computed(() => Math.max(1, Math.ceil((response.value?.total ?? 0) / pageSize.value)));
const pageLinks = computed(() => {
  const last = totalPages.value;
  const current = page.value;
  if (last <= 7) return Array.from({ length: last }, (_, index) => index + 1);
  if (current <= 4) return [1, 2, 3, 4, 5, 'after', last];
  if (current >= last - 3) return [1, 'before', last - 4, last - 3, last - 2, last - 1, last];
  return [1, 'before', current - 1, current, current + 1, 'after', last];
});
const needsRestocking = computed(() => counts.value ? counts.value.critical + counts.value.warning : null);
const selectedOnPage = computed(() => rows.value.filter((row) => selected.value.has(rowKey(row))).length);
const isPageSelected = computed(() => rows.value.length > 0 && selectedOnPage.value === rows.value.length);
const isPagePartlySelected = computed(() => selectedOnPage.value > 0 && !isPageSelected.value);

watch([query, warehouseId, status], ([newQuery, newWarehouse, newStatus], previous) => {
  const [oldQuery, oldWarehouse, oldStatus] = previous ?? [];
  const searchChanged = oldQuery !== undefined && newQuery !== oldQuery;
  clearSelection();
  page.value = 1;
  if (props.active) {
    startLoad(searchChanged && newWarehouse === oldWarehouse && newStatus === oldStatus ? SEARCH_DELAY_MS : 0);
  }
}, { immediate: true });

watch(() => props.active, (active) => {
  if (active) void reload();
});

watch(canEdit, (editable) => {
  if (!editable) clearSelection();
});

onBeforeUnmount(() => {
  disposed = true;
  ++latestRequest;
  clearTimeout(searchTimer);
});

function startLoad(delay = 0) {
  clearTimeout(searchTimer);
  const requestId = ++latestRequest;
  loading.value = true;
  loadingDifferentPage.value = true;
  listError.value = '';
  if (delay) searchTimer = setTimeout(() => void load(requestId), delay);
  else void load(requestId);
}

async function reload() {
  clearTimeout(searchTimer);
  loading.value = true;
  listError.value = '';
  await load(++latestRequest);
}

async function load(requestId: number) {
  try {
    const result = await replenishmentService.list({
      page: page.value,
      size: pageSize.value,
      keyword: query.value.trim() || undefined,
      warehouseId: warehouseId.value || undefined,
      status: status.value || undefined
    });
    if (requestId !== latestRequest) return;
    const lastPage = Math.max(1, Math.ceil(result.total / pageSize.value));
    if (page.value > lastPage) {
      page.value = lastPage;
      await load(requestId);
      return;
    }
    response.value = result;
    page.value = result.page;
    loadingDifferentPage.value = false;
  } catch (error) {
    if (requestId !== latestRequest) return;
    listError.value = error instanceof Error ? error.message : '请稍后重试。';
  } finally {
    if (requestId === latestRequest) loading.value = false;
  }
}

function changePage(next: number) {
  if (loading.value || listError.value || next < 1 || next > totalPages.value || next === page.value) return;
  page.value = next;
  startLoad();
}

function changePageSize(event: Event) {
  const size = Number((event.target as HTMLSelectElement).value) as PageSize;
  if (!PAGE_SIZES.includes(size) || size === pageSize.value) return;
  pageSize.value = size;
  page.value = 1;
  startLoad();
}

function resetFilters() {
  if (query.value || warehouseId.value || status.value) {
    query.value = '';
    warehouseId.value = '';
    status.value = '';
  } else if (page.value !== 1) changePage(1);
}

function rowKey(row: ReplenishmentRow) {
  return `${row.skuId}:${row.warehouseId ?? 'shared'}`;
}

function clearSelection() {
  if (selected.value.size) selected.value = new Map();
  selectionError.value = '';
}

function updateSelection(records: ReplenishmentRow[], checked: boolean) {
  if (!canEdit.value || loading.value || saving.value) return false;
  const next = new Map(selected.value);
  for (const row of records) {
    const key = rowKey(row);
    if (checked) next.set(key, row);
    else next.delete(key);
  }
  if (next.size > MAX_BATCH_SIZE) {
    selectionError.value = `每次最多选择 ${MAX_BATCH_SIZE} 条。请取消部分已选记录后重试。`;
    return false;
  }
  selected.value = next;
  selectionError.value = '';
  return true;
}

function selectRow(row: ReplenishmentRow, event: Event) {
  const input = event.target as HTMLInputElement;
  if (!updateSelection([row], input.checked)) input.checked = selected.value.has(rowKey(row));
}

function selectCurrentPage(event: Event) {
  const input = event.target as HTMLInputElement;
  if (!updateSelection(rows.value, input.checked)) {
    input.checked = isPageSelected.value;
    input.indeterminate = isPagePartlySelected.value;
  }
}

function warehouseNames(row: ReplenishmentRow) {
  return row.warehouses.map((warehouse) => warehouse.name).join('、') || '—';
}

function formatStock(row: ReplenishmentRow) {
  const quantity = /^(-?\d+)(?:\.(\d{1,4}))?$/.exec(row.stockExact ?? '');
  if (!quantity) return format(row.stock);
  const [, whole, decimal] = quantity;
  const fraction = decimal?.replace(/0+$/, '');
  return `${numberFormat.format(BigInt(whole))}${fraction ? `.${fraction}` : ''}`;
}

function openSettings(row: ReplenishmentRow) {
  if (!canEdit.value || loading.value || saving.value) return;
  saveError.value = '';
  settings.value = { row };
}

function openBatchSettings() {
  if (!canEdit.value || loading.value || saving.value || listError.value) return;
  const chosen = [...selected.value.values()];
  const row = chosen[0];
  if (!row) return;
  saveError.value = '';
  settings.value = { row, targets: chosen.map(({ skuId, warehouseId }) => ({ skuId, warehouseId })) };
}

async function closeSettings() {
  const previous = settings.value;
  settings.value = null;
  await nextTick();
  if (disposed || !previous) return;
  const action = previous.targets ? batchButton.value
    : root.value?.querySelector<HTMLButtonElement>(`[data-settings-key="${rowKey(previous.row)}"]`);
  if (action && !action.disabled) action.focus();
  else searchField.value?.focus();
}

async function saveSettings(config: ReplenishmentConfig) {
  if (!settings.value || saving.value || !canEdit.value) return;
  const { row, targets } = settings.value;
  saving.value = true;
  saveError.value = '';
  try {
    if (targets) await replenishmentService.saveBatch(targets, config);
    else await replenishmentService.save(row.skuId, row.warehouseId, config);
  } catch (error) {
    if (!disposed) saveError.value = error instanceof Error ? error.message : '保存失败，请重试。';
    saving.value = false;
    return;
  }
  if (disposed) return;
  if (targets) {
    clearSelection();
    message.success(`已保存 ${targets.length} 条补货设置`);
  } else message.success(`${row.sku} 的补货设置已保存`);
  await reload();
  saving.value = false;
  await closeSettings();
}
</script>

<template>
  <section ref="root" class="rp-page" aria-labelledby="rp-title" data-testid="replenishment-alerts">
    <header class="rp-page-header">
      <h1 id="rp-title">补货预警</h1>
      <p>
        <template v-if="needsRestocking !== null">
          <strong>{{ format(needsRestocking) }} 条库存记录已达到补货条件</strong><span class="rp-header-divider">·</span>
        </template>
        按 SKU 查看库存与生效阈值
      </p>
    </header>

    <div class="rp-status-strip" role="group" aria-label="按预警状态筛选">
      <button v-for="(item, key) in statuses" :key="key" :class="`rp-summary rp-tone-${key}`" type="button"
        :aria-pressed="status === key" @click="status = status === key ? '' : key">
        <span class="rp-summary-label"><component :is="item.icon" :size="15" aria-hidden="true" />{{ item.label }}</span>
        <span class="rp-summary-value"><strong>{{ format(counts?.[key] ?? null) }}</strong><span>条</span></span>
      </button>
    </div>

    <section class="rp-sheet" aria-labelledby="rp-list-title">
      <div class="rp-list-top">
        <div class="rp-list-heading">
          <h2 id="rp-list-title">SKU 预警列表</h2>
          <p>共用库存按 SKU 合并，独立补货按 SKU × 仓库分开。</p>
        </div>
        <div class="rp-filters" role="group" aria-label="列表筛选">
          <label class="rp-field rp-search-field">
            <Search :size="16" aria-hidden="true" />
            <input ref="searchField" v-model="query" type="search" placeholder="搜索 SKU 或商品名称" aria-label="搜索 SKU 或商品名称" />
          </label>
          <label class="rp-field rp-select-field">
            <select v-model="warehouseId" aria-label="仓库">
              <option value="">全部仓库</option>
              <option v-for="item in response?.warehouses" :key="item.id" :value="item.id">{{ item.name }}</option>
            </select><ChevronDown :size="14" aria-hidden="true" />
          </label>
          <label class="rp-field rp-select-field">
            <select v-model="status" aria-label="预警状态">
              <option value="">全部状态</option>
              <option v-for="(item, key) in statuses" :key="key" :value="key">{{ item.label }}</option>
            </select><ChevronDown :size="14" aria-hidden="true" />
          </label>
          <button type="button" class="rp-reset" @click="resetFilters">重置</button>
          <span class="rp-result-count" aria-live="polite">
            <template v-if="loading">正在加载…</template>
            <template v-else-if="results && !listError">显示 {{ format(rows.length) }} / {{ format(results.total) }} 条</template>
          </span>
        </div>
      </div>

      <div v-if="canEdit" class="rp-batch-bar" role="group" aria-label="批量设置补货">
        <div class="rp-batch-selection">
          <label class="rp-select-page">
            <input type="checkbox" aria-label="全选本页" :checked="isPageSelected" :indeterminate.prop="isPagePartlySelected"
              :disabled="!rows.length || loading || saving || Boolean(listError)" @change="selectCurrentPage" />
            <span>全选本页</span>
          </label>
          <span class="rp-selection-count" aria-live="polite">已选 <strong>{{ format(selected.size) }}</strong> 条</span>
        </div>
        <div class="rp-batch-actions">
          <button type="button" class="rp-clear-selection" :disabled="!selected.size || saving" @click="clearSelection">清空</button>
          <button ref="batchButton" type="button" class="rp-batch-button" data-testid="replenishment-bulk-settings"
            :disabled="!selected.size || loading || saving || Boolean(listError)" @click="openBatchSettings">批量设置</button>
        </div>
        <p v-if="selectionError" class="rp-selection-error" role="alert">{{ selectionError }}</p>
      </div>

      <table class="rp-table" :class="{ 'rp-table--selectable': canEdit }" aria-labelledby="rp-list-title" :aria-busy="loading">
        <thead><tr>
          <th v-if="canEdit" scope="col" class="rp-col-select"><span class="sr-only">选择记录</span></th>
          <th scope="col" class="rp-col-sku">SKU / 商品</th>
          <th scope="col" class="rp-col-warehouse">仓库</th>
          <th scope="col" class="rp-number rp-col-number">当前库存</th>
          <th scope="col" class="rp-number rp-col-safety">生效安全库存</th>
          <th scope="col" class="rp-number rp-col-number">补货点</th>
          <th scope="col" class="rp-col-status">预警状态</th>
          <th v-if="canEdit" scope="col" class="rp-col-action">操作</th>
        </tr></thead>
        <tbody v-if="rows.length && !listError">
          <tr v-for="row in rows" :key="rowKey(row)" :data-sku="row.sku" :data-status="row.status"
            :class="{ 'rp-row-selected': canEdit && selected.has(rowKey(row)) }">
            <td v-if="canEdit" class="rp-select-cell">
              <label class="rp-select-row">
                <input type="checkbox" :data-selection-key="rowKey(row)" :checked="selected.has(rowKey(row))"
                  :aria-label="`选择 ${row.sku} ${row.warehouseId === null ? '共用库存' : warehouseNames(row)}`"
                  :disabled="loading || saving" @change="selectRow(row, $event)" />
              </label>
            </td>
            <th scope="row" class="rp-sku-cell"><strong>{{ row.sku }}</strong><span>{{ row.name }}</span></th>
            <td class="rp-warehouse-cell">
              <strong>{{ warehouseNames(row) }}</strong>
              <span>{{ row.warehouseId === null ? '共用库存 · SKU' : '独立补货 · SKU × 仓库' }}</span>
            </td>
            <td class="rp-number rp-stock-cell">
              <span class="rp-mobile-label">当前库存</span>
              <strong :class="`rp-stock rp-tone-${row.status}`">{{ formatStock(row) }}</strong><span class="rp-unit"> 个</span>
            </td>
            <td class="rp-number rp-safety-cell">
              <span class="rp-mobile-label">生效安全库存</span>
              <strong :class="{ 'rp-dash': row.safety == null }">{{ format(row.safety) }}</strong><span v-if="row.safety != null" class="rp-unit"> 个</span>
              <small v-if="sources[row.source]" class="rp-source">{{ sources[row.source] }}</small>
            </td>
            <td class="rp-number rp-point-cell">
              <span class="rp-mobile-label">补货点</span>
              <strong :class="{ 'rp-dash': row.point == null }">{{ format(row.point) }}</strong><span v-if="row.point != null" class="rp-unit"> 个</span>
            </td>
            <td class="rp-status-cell">
              <span class="rp-badge" :class="`rp-tone-${row.status}`" :title="statuses[row.status].hint">
                <span aria-hidden="true"></span>{{ statuses[row.status].label }}
              </span>
            </td>
            <td v-if="canEdit" class="rp-action-cell">
              <button type="button" class="rp-setting" :disabled="loading || saving" :data-settings-key="rowKey(row)"
                :aria-label="`设置 ${row.sku} ${warehouseNames(row)} 的安全库存`" @click="openSettings(row)">
                <Settings2 :size="15" aria-hidden="true" /><span>设置</span>
              </button>
            </td>
          </tr>
        </tbody>
        <tbody v-else-if="loading" aria-hidden="true" class="rp-skeleton">
          <tr v-for="index in 3" :key="index">
            <td v-for="cell in (canEdit ? 8 : 6)" :key="cell"><span></span></td>
          </tr>
        </tbody>
      </table>

      <div v-if="listError" class="rp-empty" role="alert">
        <CircleAlert :size="22" aria-hidden="true" /><strong>补货预警加载失败</strong><span>{{ listError }}</span>
        <button type="button" class="rp-reset" @click="reload">重试</button>
      </div>
      <div v-else-if="!loading && !rows.length" class="rp-empty">
        <Search :size="22" aria-hidden="true" /><strong>{{ hasFilters ? '没有符合条件的 SKU' : '暂无 SKU 库存记录' }}</strong>
        <span>查询尚未入库的 SKU 时，请先选择独立补货仓，再搜索 SKU 或商品名称。</span>
        <template v-if="hasFilters">
          <button type="button" class="rp-reset" @click="resetFilters">清除筛选</button>
        </template>
      </div>

      <footer class="rp-list-footer">
        <p><CircleHelp :size="15" aria-hidden="true" /><span>库存达到或低于安全库存时为紧急补货；高于安全库存且达到或低于补货点时为建议补货。</span></p>
        <nav class="rp-pagination" aria-label="补货预警分页">
          <div class="rp-page-links">
            <button type="button" class="rp-reset" aria-label="上一页" :aria-disabled="loading || Boolean(listError) || page <= 1" @click="changePage(page - 1)">
              <ChevronLeft :size="16" aria-hidden="true" />
            </button>
            <template v-for="link in pageLinks" :key="link">
              <button v-if="typeof link === 'number'" type="button" class="rp-reset" :aria-label="`第 ${link} 页`"
                :aria-current="page === link ? 'page' : undefined" :aria-disabled="loading || Boolean(listError)" @click="changePage(link)">{{ format(link) }}</button>
              <span v-else class="rp-page-ellipsis" aria-hidden="true">…</span>
            </template>
            <button type="button" class="rp-reset" aria-label="下一页" :aria-disabled="loading || Boolean(listError) || page >= totalPages" @click="changePage(page + 1)">
              <ChevronRight :size="16" aria-hidden="true" />
            </button>
          </div>
          <label class="rp-field rp-select-field rp-page-size">
            <select :value="pageSize" aria-label="每页条数" @change="changePageSize">
              <option v-for="size in PAGE_SIZES" :key="size" :value="size">{{ size }} 条 / 页</option>
            </select>
            <ChevronDown :size="14" aria-hidden="true" />
          </label>
        </nav>
      </footer>
    </section>

    <ReplenishmentSettings v-if="settings" :row="settings.row" :bulk-count="settings.targets?.length" :saving="saving" :save-error="saveError"
      @close="closeSettings" @save="saveSettings" />
  </section>
</template>

<style scoped src="./replenishmentAlerts.css"></style>
