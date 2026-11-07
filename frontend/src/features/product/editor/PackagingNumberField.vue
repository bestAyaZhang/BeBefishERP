<script setup lang="ts">
import { computed } from 'vue';
import { MAX_CARTON_QUANTITY, MAX_SAFE_DIMENSION, MAX_SAFE_VOLUME } from './productEditorState';

const props = defineProps<{
  id: string;
  label: string;
  value: number | null | undefined;
  unit: string;
  dimension?: boolean;
  volume?: boolean;
  quantity?: boolean;
  error?: string;
}>();
const emit = defineEmits<{ change: [value: number | null] }>();
const max = computed(() => props.quantity ? MAX_CARTON_QUANTITY : props.volume ? MAX_SAFE_VOLUME : MAX_SAFE_DIMENSION);

function update(event: Event) {
  const input = event.target as HTMLInputElement;
  emit('change', input.value === '' ? null : input.valueAsNumber);
}
</script>

<template>
  <label class="pack-number">
    <span :data-dimension-label="dimension ? '' : undefined">{{ label }}</span>
    <span class="pack-number-control">
      <input
        :data-testid="id"
        :value="value ?? ''"
        type="number"
        :min="quantity ? 1 : 0"
        :max="max"
        :step="quantity ? 1 : 0.001"
        :inputmode="quantity ? 'numeric' : 'decimal'"
        placeholder="未填写"
        :aria-invalid="error ? true : undefined"
        @input="update"
      />
      <span :data-testid="id + '-unit'" class="pack-number-unit">{{ unit }}</span>
    </span>
    <small v-if="error" :data-testid="id + '-error'" class="text-red-700">{{ error }}</small>
  </label>
</template>

<style scoped>
.pack-number { display: flex; min-width: 0; flex-direction: column; gap: 6px; color: #42556b; font-size: 13px; }
.pack-number-control { position: relative; display: block; }
.pack-number input { width: 100%; min-width: 0; height: 40px; padding: 0 32px 0 10px; border: 1px solid #d7e0eb; border-radius: 6px; color: #20334b; background: #fff; font-variant-numeric: tabular-nums; }
.pack-number input:focus { outline: 2px solid #bfdaf0; outline-offset: 1px; border-color: #0968b6; }
.pack-number-unit { position: absolute; right: 8px; top: 50%; transform: translateY(-50%); color: #65758a; font-size: 11px; pointer-events: none; }
.pack-number small { line-height: 1.45; }
</style>
