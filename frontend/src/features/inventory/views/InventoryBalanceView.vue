<script setup lang="ts">
import { computed, defineAsyncComponent, nextTick, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import WarehouseCanvasView from './WarehouseCanvasView.vue';

const ReplenishmentAlerts = defineAsyncComponent(() => import('../replenishment/ReplenishmentAlerts.vue'));
const route = useRoute();
const router = useRouter();
const tabs = [{ id: 'balance', label: '库存余额' }, { id: 'replenishment', label: '补货预警' }] as const;
type Tab = typeof tabs[number]['id'];
const tab = computed<Tab>(() => route.query.tab === 'replenishment' ? 'replenishment' : 'balance');
const visited = ref<Record<Tab, boolean>>({ balance: false, replenishment: false });
watch(tab, (active) => { visited.value[active] = true; }, { immediate: true });

async function selectTab(selected: Tab) {
  if (selected === tab.value) return;
  await router.replace({ query: { ...route.query, tab: selected === 'balance' ? undefined : selected } });
}

async function onTabKey(event: KeyboardEvent) {
  const targets: Record<string, Tab> = {
    Home: 'balance', End: 'replenishment',
    ArrowLeft: tab.value === 'balance' ? 'replenishment' : 'balance',
    ArrowRight: tab.value === 'balance' ? 'replenishment' : 'balance'
  };
  const target = targets[event.key];
  if (!target) return;
  event.preventDefault();
  await selectTab(target);
  await nextTick();
  document.getElementById(`inventory-tab-${target}`)?.focus();
}
</script>

<template>
  <div class="inventory-balance-page">
    <div class="inventory-tabbar" role="tablist" aria-label="库存余额视图" @keydown="onTabKey">
      <button v-for="item in tabs" :id="`inventory-tab-${item.id}`" :key="item.id" type="button" role="tab"
        :aria-selected="tab === item.id" :aria-controls="`inventory-panel-${item.id}`"
        :tabindex="tab === item.id ? 0 : -1" @click="selectTab(item.id)">
        {{ item.label }}
      </button>
    </div>
    <div v-show="tab === 'balance'" id="inventory-panel-balance" class="inventory-panel" role="tabpanel" aria-labelledby="inventory-tab-balance">
      <WarehouseCanvasView v-if="visited.balance" mode="inventory" />
    </div>
    <div v-show="tab === 'replenishment'" id="inventory-panel-replenishment" class="inventory-panel inventory-alert-panel" role="tabpanel" aria-labelledby="inventory-tab-replenishment">
      <ReplenishmentAlerts v-if="visited.replenishment" :active="tab === 'replenishment'" />
    </div>
  </div>
</template>

<style scoped>
.inventory-balance-page { display: flex; flex-direction: column; height: 100%; min-height: 0; }
.inventory-tabbar {
  display: flex; flex-shrink: 0; min-height: 49px; gap: 28px; padding-inline: 32px;
  background: #fff; border-bottom: 1px solid #e2e8f0;
}
.inventory-tabbar button {
  position: relative; min-height: 48px; white-space: nowrap; color: #58677e;
  font-size: 14px; font-weight: 600;
}
.inventory-tabbar button:hover, .inventory-tabbar button[aria-selected="true"] { color: #405bd9; }
.inventory-tabbar button:focus-visible { outline: 2px solid #536dff; outline-offset: 2px; }
.inventory-tabbar button[aria-selected="true"]::after {
  content: ''; position: absolute; inset: auto 0 -1px; height: 2px; background: #536dff; border-radius: 2px 2px 0 0;
}
.inventory-panel { flex: 1; min-height: 0; min-width: 0; }
.inventory-alert-panel { overflow-y: auto; }
@media (max-width: 1210px) { .inventory-tabbar { padding-inline: 22px; } }
@media (max-width: 660px) { .inventory-tabbar { gap: 20px; padding-inline: 16px; } }
</style>
