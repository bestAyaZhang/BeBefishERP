<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Plus, Search, Store } from 'lucide-vue-next';
import { currentUser } from '../../services/authSession';
import { platformService as service } from './platformService';
import CatalogFormDrawer from './CatalogFormDrawer.vue';
import type { CatalogItem, CatalogInput, CatalogStatus } from './types';
const route = useRoute(), router = useRouter();
const tab = ref(route.query.tab === 'shops' ? 'shops' : 'platforms');
const keyword = ref(String(route.query.keyword ?? '')), status = ref(String(route.query.status ?? ''));
const platformId = ref<number | undefined>(Number(route.query.platformId) || undefined);
const page = ref(Math.max(1, Number(route.query.page) || 1));
const rows = ref<CatalogItem[]>([]), platforms = ref<CatalogItem[]>([]), total = ref(0);
const loading = ref(false), error = ref(''), drawer = ref(false), editing = ref<CatalogItem | null>(null);
const saving = ref(false), saveError = ref(''); let generation = 0;
const shop = computed(() => tab.value === 'shops');
const canCreate = computed(() => currentUser.value?.permissions.includes('platform:create'));
const canEdit = computed(() => currentUser.value?.permissions.includes('platform:edit'));
const pages = computed(() => Math.max(1, Math.ceil(total.value / 20)));
async function loadPlatforms() {
  const all: CatalogItem[] = []; let p = 1;
  while (true) { const result = await service.listPlatforms({ page: p++, size: 100 }); all.push(...result.records); if (all.length >= result.total || !result.records.length) break; }
  platforms.value = all;
}
async function load() {
  const request = ++generation; loading.value = true; error.value = '';
  try {
    const result = await (shop.value ? service.listShops : service.listPlatforms)({ page: page.value, size: 20, keyword: keyword.value, status: status.value, platformId: shop.value ? platformId.value : undefined });
    if (request !== generation) return;
    rows.value = result.records; total.value = result.total;
  } catch (e) { if (request === generation) error.value = e instanceof Error ? e.message : '加载失败'; }
  finally { if (request === generation) loading.value = false; }
}
async function search(reset = true) {
  if (reset) page.value = 1;
  await router.replace({ query: { tab: tab.value, ...(keyword.value ? { keyword: keyword.value } : {}), ...(status.value ? { status: status.value } : {}), ...(shop.value && platformId.value ? { platformId: String(platformId.value) } : {}), page: String(page.value) } });
  await load();
}
function switchTab(value: string) { tab.value = value; keyword.value = ''; status.value = ''; void search(); }
function shopsFor(row: CatalogItem) { platformId.value = row.id; switchTab('shops'); }
function open(item: CatalogItem | null) { editing.value = item; saveError.value = ''; drawer.value = true; }
async function save(input: CatalogInput) {
  if (saving.value) return;
  saving.value = true; saveError.value = '';
  try {
    if (editing.value) {
      const update = { name: input.name, sortOrder: input.sortOrder, remark: input.remark, version: editing.value.version,
        channelType: input.channelType, ownerName: input.ownerName };
      await (shop.value ? service.updateShop : service.updatePlatform)(editing.value.id, update);
    } else await (shop.value ? service.createShop : service.createPlatform)(input);
    drawer.value = false; await Promise.all([load(), loadPlatforms()]);
  } catch (e) { saveError.value = e instanceof Error ? e.message : '保存失败'; }
  finally { saving.value = false; }
}
async function changeStatus(row: CatalogItem) {
  if (saving.value) return;
  const next: CatalogStatus = row.status === 'enabled' ? 'disabled' : 'enabled';
  if (next === 'disabled' && !window.confirm('停用“' + row.name + '”？' + (!shop.value ? '其下店铺将不再供新单选择。' : '') + '历史单据保留。')) return;
  saving.value = true; error.value = '';
  try { await (shop.value ? service.changeShopStatus : service.changePlatformStatus)(row.id, next, row.version); await Promise.all([load(), loadPlatforms()]); }
  catch (e) { error.value = e instanceof Error ? e.message : '状态修改失败'; }
  finally { saving.value = false; }
}
watch(() => route.query, q => {
  const nextTab = q.tab === 'shops' ? 'shops' : 'platforms';
  const nextPage = Math.max(1, Number(q.page) || 1);
  if (tab.value !== nextTab || page.value !== nextPage || keyword.value !== String(q.keyword ?? '') || status.value !== String(q.status ?? '') || platformId.value !== (Number(q.platformId) || undefined)) {
    tab.value = nextTab; page.value = nextPage; keyword.value = String(q.keyword ?? ''); status.value = String(q.status ?? ''); platformId.value = Number(q.platformId) || undefined; void load();
  }
});
onMounted(() => { void load(); void loadPlatforms().catch(e => { error.value = e.message; }); });
onBeforeUnmount(() => generation++);
</script>
<template>
  <section class="mx-auto max-w-[1500px] space-y-5 pb-8" data-testid="platform-management">
    <header class="flex flex-wrap items-end justify-between gap-4"><div><h1 class="text-2xl font-bold text-slate-800">平台管理</h1><p class="mt-2 text-sm text-slate-500">统一维护销售平台和所属店铺，为发货单提供准确的来源。</p></div><button v-if="canCreate" data-testid="catalog-add" class="inline-flex items-center gap-2 rounded-lg bg-[#536dff] px-5 py-3 text-sm font-semibold text-white" @click="open(null)"><Plus :size="17" />新建{{ shop ? '店铺' : '平台' }}</button></header>
    <div class="rounded-xl border border-slate-200 bg-white shadow-sm">
      <div class="flex gap-7 border-b px-6"><button v-for="t in [{value:'platforms',label:'平台'},{value:'shops',label:'店铺'}]" :key="t.value" class="border-b-2 py-4 text-sm font-semibold" :class="tab === t.value ? 'border-indigo-500 text-indigo-600' : 'border-transparent text-slate-500'" @click="switchTab(t.value)">{{ t.label }}</button></div>
      <form class="flex flex-wrap gap-3 p-5" @submit.prevent="search()"><label class="relative min-w-56 flex-1"><span class="sr-only">搜索名称</span><Search :size="16" class="absolute left-3 top-3 text-slate-400" /><input v-model="keyword" maxlength="200" placeholder="搜索名称" class="control w-full pl-9" /></label><select v-if="shop" v-model="platformId" aria-label="平台筛选" class="control"><option :value="undefined">全部平台</option><option v-for="p in platforms" :key="p.id" :value="p.id">{{ p.name }}</option></select><select v-model="status" aria-label="状态筛选" class="control"><option value="">全部状态</option><option value="enabled">启用</option><option value="disabled">停用</option></select><button class="rounded-lg bg-slate-800 px-5 py-2 text-sm text-white">查询</button></form>
      <p v-if="error" role="alert" class="mx-5 mb-4 rounded-lg bg-rose-50 p-3 text-sm text-rose-700">{{ error }} <button class="underline" @click="load">重试</button></p>
      <p v-if="loading" class="p-12 text-center text-slate-500">加载中…</p>
      <div v-else-if="!rows.length && !error" class="p-16 text-center text-slate-400"><Store :size="36" class="mx-auto mb-4" /><p>{{ keyword || status ? '没有符合条件的记录' : shop ? '暂无店铺，请先创建或启用所属平台' : '暂无平台' }}</p></div>
      <div v-else-if="!error" class="overflow-x-auto"><table class="w-full whitespace-nowrap text-left text-sm"><thead class="bg-slate-50 text-xs text-slate-500"><tr><th>名称</th><th>{{ shop ? '所属平台' : '店铺数' }}</th><th v-if="shop">渠道类型</th><th v-if="shop">店铺负责人</th><th v-if="shop">门店选项</th><th>状态</th><th>排序</th><th>更新时间</th><th class="text-right">操作</th></tr></thead><tbody><tr v-for="row in rows" :key="row.id" class="border-t border-slate-100"><td><p class="font-semibold text-slate-800">{{ row.name }}</p></td><td>{{ shop ? row.platformName : row.shopCount }}</td><td v-if="shop">{{ row.channelType === 'private' ? '私域' : '电商' }}</td><td v-if="shop">{{ row.ownerName || '—' }}</td><td v-if="shop">{{ row.optionLabel || row.name }}</td><td><span class="rounded-full px-2.5 py-1 text-xs" :class="row.status === 'enabled' && row.platformStatus !== 'disabled' ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-500'">{{ row.status === 'disabled' ? '已停用' : row.platformStatus === 'disabled' ? '平台已停用' : '已启用' }}</span></td><td>{{ row.sortOrder }}</td><td class="text-xs text-slate-500">{{ row.updatedAt?.replace('T',' ').slice(0,16) }}</td><td class="space-x-4 text-right text-indigo-600"><button v-if="!shop" :data-testid="'platform-shops-' + row.id" @click="shopsFor(row)">查看店铺</button><button v-if="canEdit" :data-testid="'catalog-edit-' + row.id" :disabled="saving" @click="open(row)">编辑</button><button v-if="canEdit" :disabled="saving" @click="changeStatus(row)">{{ row.status === 'enabled' ? '停用' : '启用' }}</button></td></tr></tbody></table></div>
      <footer class="flex items-center justify-between border-t p-5 text-sm text-slate-500"><span>共 {{ total }} 条</span><div class="flex items-center gap-4"><button :disabled="loading || page <= 1" @click="page--; search(false)">上一页</button><span>{{ page }} / {{ pages }}</span><button :disabled="loading || page >= pages" @click="page++; search(false)">下一页</button></div></footer>
    </div>
    <CatalogFormDrawer v-if="drawer" :shop="shop" :item="editing" :platforms="platforms" :initial-platform-id="platformId" :busy="saving" :error="saveError" @close="drawer = false" @save="save" />
  </section>
</template>
<style scoped>.control { @apply rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-indigo-400; } th, td { @apply px-5 py-4; } button:disabled { @apply cursor-not-allowed opacity-40; }</style>
