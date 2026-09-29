<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import type { CatalogInput, CatalogItem } from './types';
import { shopOptionLabel } from './shopOptionLabel';
const props = defineProps<{ shop: boolean; item: CatalogItem | null; platforms: CatalogItem[]; initialPlatformId?: number; busy: boolean; error: string }>();
const emit = defineEmits<{ close: []; save: [value: CatalogInput] }>();
const form = reactive<CatalogInput>({ name: props.item?.name ?? '', sortOrder: props.item?.sortOrder ?? 0,
  remark: props.item?.remark ?? '', platformId: props.item?.platformId ?? props.initialPlatformId,
  channelType: props.item?.channelType ?? 'ecommerce', ownerName: props.item?.ownerName ?? '' });
const selectedPlatform = computed(() => props.platforms.find(p => p.id === form.platformId));
const exceptionLabel = props.item?.channelType === 'private' && props.item.optionLabel && props.item.platformName
  && props.item.optionLabel !== shopOptionLabel('private', props.item.platformName, props.item.name)
  ? props.item.optionLabel : null;
const preview = computed(() => {
  if (!selectedPlatform.value || !form.name.trim()) return '填写平台和名称后自动生成';
  if (exceptionLabel && form.channelType === 'private') return exceptionLabel;
  return shopOptionLabel(form.channelType ?? 'ecommerce', selectedPlatform.value.name, form.name.trim());
});
const initial = JSON.stringify(form);
const dialog = ref<HTMLElement | null>(null);
const previousFocus = document.activeElement as HTMLElement | null;
function close() { if (!props.busy && (JSON.stringify(form) === initial || window.confirm('放弃尚未保存的修改？'))) emit('close'); }
function keyboard(e: KeyboardEvent) {
  if (e.key === 'Escape') { e.preventDefault(); close(); }
  if (e.key !== 'Tab') return;
  const items = [...dialog.value?.querySelectorAll<HTMLElement>('button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled)') ?? []];
  const first = items[0], last = items.at(-1);
  if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last?.focus(); }
  else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first?.focus(); }
}
onMounted(() => nextTick(() => dialog.value?.querySelector<HTMLElement>('input:not(:disabled)')?.focus()));
onBeforeUnmount(() => previousFocus?.focus());
</script>
<template>
  <div class="fixed inset-0 z-50 flex justify-end bg-slate-950/30" @click.self="close">
    <aside ref="dialog" role="dialog" aria-modal="true" aria-labelledby="catalog-drawer-title" class="flex h-full w-full max-w-lg flex-col bg-white shadow-xl" @keydown="keyboard">
      <header class="flex items-center justify-between border-b p-6"><h2 id="catalog-drawer-title" class="text-xl font-bold">{{ item ? '编辑' : '新建' }}{{ shop ? '店铺' : '平台' }}</h2><button :disabled="busy" aria-label="关闭" @click="close">✕</button></header>
      <form data-testid="catalog-form" class="flex min-h-0 flex-1 flex-col" @submit.prevent="emit('save', { ...form })">
        <div class="flex-1 space-y-5 overflow-y-auto p-6">
          <p v-if="error" role="alert" class="rounded-lg bg-rose-50 p-3 text-sm text-rose-700">{{ error }}</p>
          <label v-if="shop" class="block text-sm">所属平台 <span class="text-rose-500">*</span><select v-model="form.platformId" :disabled="busy || !!item" required class="field"><option :value="undefined">请选择平台</option><option v-for="p in platforms.filter(p => p.status === 'enabled' || p.id === form.platformId)" :key="p.id" :value="p.id">{{ p.name }}{{ p.status === 'disabled' ? '（已停用）' : '' }}</option></select></label>
          <label class="block text-sm">名称 <span class="text-rose-500">*</span><input v-model="form.name" data-testid="catalog-name" :disabled="busy" required :maxlength="shop ? 200 : 100" class="field" /></label>
          <template v-if="shop">
            <label class="block text-sm">渠道类型 <span class="text-rose-500">*</span><select v-model="form.channelType" data-testid="catalog-channel-type" :disabled="busy" required class="field"><option value="ecommerce">电商</option><option value="private">私域</option></select></label>
            <label class="block text-sm">店铺负责人 <span class="text-rose-500">*</span><input v-model="form.ownerName" data-testid="catalog-owner-name" :disabled="busy" required maxlength="100" class="field" /></label>
            <div class="text-sm"><span>门店选项（自动生成）</span><output data-testid="catalog-option-label" class="field break-all bg-slate-50 text-slate-700">{{ preview }}</output><p v-if="exceptionLabel && form.channelType === 'private'" class="mt-1 text-xs text-slate-500">此店铺保留原有的私域门店选项。</p></div>
          </template>
          <label class="block text-sm">排序<input v-model.number="form.sortOrder" :disabled="busy" type="number" min="0" max="9999" step="1" required class="field" /></label>
          <label class="block text-sm">备注<textarea v-model="form.remark" :disabled="busy" maxlength="1000" rows="4" class="field" /></label>
          <p class="text-xs leading-6 text-slate-500">编码由系统自动生成。改名不会改写历史发货单。{{ shop ? '店铺创建后不可更换所属平台。' : '停用平台后，其下店铺不再供新单选择。' }}</p>
        </div>
        <footer class="flex justify-end gap-3 border-t p-5"><button type="button" :disabled="busy" class="rounded-lg border px-4 py-2" @click="close">取消</button><button :disabled="busy" class="rounded-lg bg-[#536dff] px-5 py-2 text-white">{{ busy ? '保存中…' : '保存' }}</button></footer>
      </form>
    </aside>
  </div>
</template>
<style scoped>.field { @apply mt-2 block w-full rounded-lg border border-slate-200 px-3 py-2.5 outline-none focus:border-indigo-400 disabled:bg-slate-50 disabled:text-slate-500; }</style>
