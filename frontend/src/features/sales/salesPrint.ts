import printJS from 'print-js';
import salesPrintStyle from './sales-print.css?raw';

export interface SalesPrintOptions {
  onPrintDialogClose?: () => void;
  onError?: (error: unknown) => void;
}

export function printSalesDocument(element: HTMLElement | null, options: SalesPrintOptions = {}) {
  if (!element?.id || !document.body.contains(element)) {
    throw new Error('打印文档未准备好');
  }

  printJS({
    printable: element.outerHTML,
    type: 'raw-html',
    documentTitle: '出库送货单',
    scanStyles: false,
    style: salesPrintStyle,
    onPrintDialogClose: options.onPrintDialogClose,
    onError: options.onError
  });
}
