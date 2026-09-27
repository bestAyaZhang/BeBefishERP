<script setup lang="ts">
import { ref } from 'vue';
import { ScanLine } from 'lucide-vue-next';
import { parseRecipientText } from './shippingRecipientParser';
import type { RecipientFields } from './types';

const props = withDefaults(defineProps<{ modelValue: RecipientFields; disabled?: boolean }>(), { disabled: false });
const emit = defineEmits<{ 'update:modelValue': [value: RecipientFields] }>();
const raw = ref('');
const recognition = ref<'none' | 'complete' | 'partial'>('none');
const keys: Array<keyof RecipientFields> = [
  'recipientName', 'recipientPhone', 'recipientProvince', 'recipientCity', 'recipientCounty', 'recipientDetailAddress'
];

function update(key: keyof RecipientFields, event: Event) {
  emit('update:modelValue', { ...props.modelValue, [key]: (event.target as HTMLInputElement).value });
}

function recognize() {
  const parsed = parseRecipientText(raw.value);
  const next = { ...props.modelValue };
  let found = 0;
  for (const key of keys) {
    if (parsed[key].trim()) { next[key] = parsed[key]; found++; }
  }
  emit('update:modelValue', next);
  recognition.value = found === keys.length ? 'complete' : 'partial';
}

function recognizeAgain() {
  recognition.value = 'none';
}
</script>

<template>
  <section class="space-y-5">
    <div>
      <label for="recipient-raw" class="field-label">收件信息原文</label>
      <div class="mt-2 flex flex-col gap-3 lg:flex-row lg:items-stretch">
        <textarea id="recipient-raw" v-model="raw" data-testid="recipient-raw" :disabled="disabled" rows="3"
          class="field-input mt-0 min-h-24 flex-1 resize-y" placeholder="粘贴姓名、电话和完整地址，可一行或多行" />
        <button v-if="recognition === 'none'" type="button" data-testid="recipient-recognize" :disabled="disabled || !raw.trim()"
          class="inline-flex min-w-32 items-center justify-center gap-2 rounded-lg bg-indigo-50 px-5 py-3 text-sm font-semibold text-[#536dff] disabled:cursor-not-allowed disabled:opacity-40"
          @click="recognize"><ScanLine :size="17" />智能识别</button>
        <div v-else class="flex min-w-32 flex-col items-center justify-center gap-1 rounded-lg bg-emerald-50 px-5 py-2 text-sm text-emerald-700">
          <span data-testid="recipient-recognized" class="font-semibold">已识别</span>
          <button type="button" data-testid="recipient-recognize-again" :disabled="disabled"
            class="text-xs underline underline-offset-2 disabled:opacity-40" @click="recognizeAgain">重新识别</button>
        </div>
      </div>
      <p v-if="recognition !== 'none'" class="mt-2 text-xs" :class="recognition === 'complete' ? 'text-emerald-600' : 'text-amber-600'">
        {{ recognition === 'complete' ? '已识别，请核对' : '已识别部分信息，请补充标红字段' }}
      </p>
    </div>

    <div class="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
      <label class="field-label">姓名 <span class="text-rose-500">*</span><input :value="modelValue.recipientName" data-testid="recipient-name" :disabled="disabled" maxlength="100" class="field-input" @input="update('recipientName', $event)" /></label>
      <label class="field-label">电话 <span class="text-rose-500">*</span><input :value="modelValue.recipientPhone" data-testid="recipient-phone" :disabled="disabled" maxlength="50" class="field-input" @input="update('recipientPhone', $event)" /></label>
      <label class="field-label">省 <span class="text-rose-500">*</span><input :value="modelValue.recipientProvince" data-testid="recipient-province" :disabled="disabled" maxlength="30" class="field-input" @input="update('recipientProvince', $event)" /></label>
      <label class="field-label">市 <span class="text-rose-500">*</span><input :value="modelValue.recipientCity" data-testid="recipient-city" :disabled="disabled" maxlength="30" class="field-input" @input="update('recipientCity', $event)" /></label>
      <label class="field-label">区 / 县 <span class="text-rose-500">*</span><input :value="modelValue.recipientCounty" data-testid="recipient-county" :disabled="disabled" maxlength="30" class="field-input" @input="update('recipientCounty', $event)" /></label>
      <label class="field-label">详细地址 <span class="text-rose-500">*</span><input :value="modelValue.recipientDetailAddress" data-testid="recipient-detail-address" :disabled="disabled" maxlength="100" class="field-input" @input="update('recipientDetailAddress', $event)" /></label>
    </div>
  </section>
</template>

<style scoped>
.field-label { @apply text-sm font-medium text-slate-600; }
.field-input { @apply mt-1.5 block w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm font-normal text-slate-800 outline-none transition focus:border-[#536dff] focus:ring-2 focus:ring-[#536dff]/10 disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-500; }
</style>
