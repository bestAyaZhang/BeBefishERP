<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { Download, Printer, X } from 'lucide-vue-next';
import { closePrintPreview, printPreview, printPreviewHtml } from './printPreview';

const sheet = ref<HTMLElement | null>(null);
const closeButton = ref<HTMLButtonElement | null>(null);
const notice = ref('');
let previousFocus: HTMLElement | null = null;
let pageStyle: HTMLStyleElement | null = null;
let originalTitle: string | null = null;

function cleanup() {
  document.body.classList.remove('erp-print-preview-active');
  pageStyle?.remove(); pageStyle = null;
  if (originalTitle !== null) document.title = originalTitle;
  originalTitle = null;
  previousFocus?.focus(); previousFocus = null;
}

watch(printPreview, async (job, oldJob) => {
  if (oldJob) cleanup();
  notice.value = '';
  if (!job) return;
  previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
  originalTitle = document.title;
  document.title = job.documentTitle;
  document.body.classList.add('erp-print-preview-active');
  pageStyle = document.createElement('style');
  pageStyle.textContent = (job.style.match(/@page\s*\{[^}]*\}/g) ?? []).join('\n');
  document.head.append(pageStyle);
  await nextTick();
  if (printPreview.value !== job || !sheet.value) return;
  // Render directly in the page: no hidden iframe, onload race or popup dependency.
  const shadow = sheet.value.shadowRoot ?? sheet.value.attachShadow({ mode: 'open' });
  const style = document.createElement('style');
  style.textContent = ':host{display:block;font:12px Arial,"Microsoft YaHei",sans-serif;color:#172033;background:white;padding:14mm;box-sizing:border-box}*{box-sizing:border-box}'
    + job.style.replace(/\b(?:html|body)\b(?=\s*[,\{])/g, ':host')
    + '@media print{:host{padding:0}}';
  const content = document.createElement('div');
  content.innerHTML = job.printable;
  shadow.replaceChildren(style, content);
  closeButton.value?.focus();
}, { immediate: true });

async function print() {
  if (!printPreview.value) return;
  notice.value = '如果未出现系统打印窗口，请下载送货单，在 Chrome 或 Edge 中打开后打印。';
  await nextTick();
  if (!printPreview.value) return;
  try { window.print(); }
  catch (error) {
    notice.value = '当前浏览器未能打开打印窗口，请下载单据后使用 Chrome 或 Edge 打印。';
    printPreview.value?.onError?.(error);
  }
}

function download() {
  if (!printPreview.value) return;
  const url = URL.createObjectURL(new Blob([printPreviewHtml(printPreview.value)], { type: 'text/html;charset=utf-8' }));
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = printPreview.value.documentTitle.replace(/[\\/:*?"<>|]/g, '_') + '.html';
  document.body.append(anchor); anchor.click(); anchor.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function keydown(event: KeyboardEvent) {
  if (!printPreview.value) return;
  if (event.key === 'Escape') { event.preventDefault(); closePrintPreview(); }
  if (event.key === 'Tab') {
    const buttons = Array.from(document.querySelectorAll<HTMLButtonElement>('#erp-print-preview button'));
    const first = buttons[0]; const last = buttons[buttons.length - 1];
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
  }
}
document.addEventListener('keydown', keydown);
onBeforeUnmount(() => { document.removeEventListener('keydown', keydown); closePrintPreview(); cleanup(); });
</script>

<template>
  <Teleport to="body">
    <section v-if="printPreview" id="erp-print-preview" role="dialog" aria-modal="true" aria-labelledby="print-preview-title" data-testid="print-preview" class="fixed inset-0 z-[200] flex flex-col bg-slate-100 text-slate-800">
      <header class="print-preview-toolbar flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 bg-white px-5 py-4">
        <div><h2 id="print-preview-title" class="font-bold">{{ printPreview.documentTitle }} · 打印预览</h2><p class="mt-1 text-xs text-slate-500">A4 纵向 · 核对内容后打印，也可下载单据用浏览器打开</p></div>
        <div class="flex gap-3">
          <button data-testid="print-preview-print" class="inline-flex items-center gap-2 rounded-lg bg-[#536dff] px-4 py-2 text-sm font-bold text-white" @click="print"><Printer class="h-4 w-4" />打印</button>
          <button data-testid="print-preview-download" class="inline-flex items-center gap-2 rounded-lg border border-slate-200 px-4 py-2 text-sm font-bold" @click="download"><Download class="h-4 w-4" />下载单据</button>
          <button ref="closeButton" data-testid="print-preview-close" aria-label="关闭打印预览" class="rounded-lg border border-slate-200 p-2" @click="closePrintPreview"><X class="h-5 w-5" /></button>
        </div>
        <p v-if="notice" role="status" class="w-full text-sm text-slate-600">{{ notice }}</p>
      </header>
      <div class="print-preview-scroll flex-1 overflow-auto p-5">
        <div ref="sheet" data-testid="print-preview-sheet" class="print-preview-sheet mx-auto min-h-[297mm] w-[210mm] bg-white shadow-lg" />
      </div>
    </section>
  </Teleport>
</template>

<style>
@media print {
  body.erp-print-preview-active { margin: 0 !important; padding: 0 !important; background: white !important; }
  body.erp-print-preview-active > :not(#erp-print-preview) { display: none !important; }
  #erp-print-preview { position: static !important; display: block !important; background: white !important; }
  #erp-print-preview .print-preview-toolbar { display: none !important; }
  #erp-print-preview .print-preview-scroll { display: block !important; overflow: visible !important; padding: 0 !important; }
  #erp-print-preview .print-preview-sheet { width: 100% !important; min-height: 0 !important; margin: 0 !important; box-shadow: none !important; }
}
</style>
