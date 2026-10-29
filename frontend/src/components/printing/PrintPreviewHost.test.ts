import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import PrintPreviewHost from './PrintPreviewHost.vue';
import { closePrintPreview, printPreviewHtml, showPrintPreview } from './printPreview';

let wrapper: ReturnType<typeof mount> | null = null;
const job = { documentTitle: 'SO-001－出库送货单', printable: '<h1>出库送货单</h1><table><tr><td>历史玻璃杯</td><td>122.88</td></tr></table>', style: '@page{size:A4 portrait;margin:14mm}body{font-size:12px}table{width:100%}' };
afterEach(() => { closePrintPreview(); wrapper?.unmount(); wrapper = null; document.body.innerHTML = ''; vi.restoreAllMocks(); });

describe('print preview', () => {
  it('renders the complete document immediately without an iframe or automatic printing', async () => {
    const print = vi.spyOn(window, 'print').mockImplementation(() => {});
    const originalTitle = document.title;
    wrapper = mount(PrintPreviewHost);
    showPrintPreview(job); await flushPromises();
    const sheet = document.querySelector('[data-testid="print-preview-sheet"]')!;
    expect(sheet.shadowRoot?.textContent).toContain('历史玻璃杯');
    expect(sheet.shadowRoot?.textContent).toContain('122.88');
    expect(document.querySelector('iframe')).toBeNull();
    expect(print).not.toHaveBeenCalled();
    expect(document.title).toBe(job.documentTitle);
    expect(document.body.classList.contains('erp-print-preview-active')).toBe(true);
    (document.querySelector('[data-testid="print-preview-print"]') as HTMLButtonElement).click(); await flushPromises();
    expect(print).toHaveBeenCalledOnce();
    expect(document.body.textContent).toContain('Chrome 或 Edge');
    closePrintPreview(); await flushPromises();
    expect(document.querySelector('[data-testid="print-preview"]')).toBeNull();
    expect(document.title).toBe(originalTitle);
    expect(document.body.classList.contains('erp-print-preview-active')).toBe(false);
  });

  it('replaces the preview document and closes safely with Escape', async () => {
    const onClose = vi.fn();
    wrapper = mount(PrintPreviewHost);
    showPrintPreview({ ...job, onPrintDialogClose: onClose }); await flushPromises();
    showPrintPreview({ ...job, printable: '<h1>另一客户的对账单</h1>' }); await flushPromises();
    const text = document.querySelector('[data-testid="print-preview-sheet"]')?.shadowRoot?.textContent;
    expect(text).toContain('另一客户的对账单'); expect(text).not.toContain('历史玻璃杯');
    expect(onClose).toHaveBeenCalledOnce();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' })); await flushPromises();
    expect(document.querySelector('[data-testid="print-preview"]')).toBeNull();
  });

  it('exports a standalone printable document and escapes the title', () => {
    const html = printPreviewHtml({ ...job, documentTitle: '</title><script>bad</script>' });
    expect(html).toContain('<meta charset="utf-8">');
    const exported = new DOMParser().parseFromString(html, 'text/html');
    expect(exported.body.querySelector('h1')?.textContent).toBe('出库送货单');
    expect(exported.body.querySelector('td')?.textContent).toBe('历史玻璃杯');
    expect(exported.body.textContent).toContain('122.88'); expect(html).toContain('size:A4');
    expect(html).not.toContain('<script>'); expect(html).toContain('&lt;/title&gt;');
  });

  it('keeps the preview available when the browser rejects printing', async () => {
    vi.spyOn(window, 'print').mockImplementation(() => { throw new Error('Unavailable'); });
    const onError = vi.fn();
    wrapper = mount(PrintPreviewHost);
    showPrintPreview({ ...job, onError }); await flushPromises();
    (document.querySelector('[data-testid="print-preview-print"]') as HTMLButtonElement).click(); await flushPromises();
    expect(onError).toHaveBeenCalledOnce();
    expect(document.body.textContent).toContain('当前浏览器未能打开打印窗口');
    expect(document.querySelector('[data-testid="print-preview-download"]')).not.toBeNull();
  });
});
