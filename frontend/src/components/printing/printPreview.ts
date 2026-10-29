import { shallowRef } from 'vue';

export interface PrintPreviewDocument {
  printable: string;
  documentTitle: string;
  style: string;
  type?: 'raw-html';
  scanStyles?: boolean;
  onPrintDialogClose?: () => void;
  onError?: (error: unknown) => void;
}

export const printPreview = shallowRef<PrintPreviewDocument | null>(null);

/** Only pass application-generated HTML with all user text escaped. */
export function showPrintPreview(document: PrintPreviewDocument) {
  if (!document.printable.trim()) throw new Error('打印文档未准备好');
  closePrintPreview();
  printPreview.value = document;
}

export function closePrintPreview() {
  const document = printPreview.value;
  printPreview.value = null;
  document?.onPrintDialogClose?.();
}

export function printPreviewHtml(document: PrintPreviewDocument) {
  const title = document.documentTitle.replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]!));
  const content = window.document.createElement('div');
  content.innerHTML = document.printable;
  // Keep local branding images usable when the downloaded document opens as a file.
  content.querySelectorAll<HTMLImageElement>('img').forEach(image => { if (image.getAttribute('src')) image.src = new URL(image.getAttribute('src')!, window.location.href).href; });
  return `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${title}</title><style>${document.style}</style></head><body>${content.innerHTML}</body></html>`;
}
