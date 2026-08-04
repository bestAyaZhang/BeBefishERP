import { afterEach, describe, expect, it, vi } from 'vitest';

const { printJsMock } = vi.hoisted(() => ({ printJsMock: vi.fn() }));

vi.mock('print-js', () => ({ default: printJsMock }));
vi.mock('./sales-print.css?raw', () => ({ default: '@page { size: A4; margin: 0; } .sales-print-document { padding: 12mm; } .sales-print-lines { table-layout: fixed; }' }));

import { printSalesDocument } from './salesPrint';

describe('salesPrint', () => {
  afterEach(() => {
    document.body.innerHTML = '';
    printJsMock.mockReset();
  });

  it('prints the complete sales document through an isolated print iframe', () => {
    const documentElement = document.createElement('article');
    documentElement.id = 'sales-print-document';
    documentElement.innerHTML = '<section>出库送货单</section><strong>¥22.00</strong>';
    document.body.append(documentElement);

    const onPrintDialogClose = vi.fn();
    const onError = vi.fn();

    printSalesDocument(documentElement, { onPrintDialogClose, onError });

    expect(printJsMock).toHaveBeenCalledWith(expect.objectContaining({
      printable: expect.stringContaining('id="sales-print-document"'),
      type: 'raw-html',
      documentTitle: '出库送货单',
      scanStyles: false,
      style: expect.stringContaining('@page { size: A4; margin: 0; }')
    }));
    expect(printJsMock.mock.calls[0][0].printable).toContain('出库送货单');
    expect(printJsMock.mock.calls[0][0].printable).not.toContain('left: -100000px');
    expect(printJsMock.mock.calls[0][0].style).toContain('.sales-print-lines');
    const [options] = printJsMock.mock.calls[0] as [{ onPrintDialogClose: () => void; onError: (error: unknown) => void }];
    options.onPrintDialogClose();
    options.onError(new Error('打印失败'));
    expect(onPrintDialogClose).toHaveBeenCalledOnce();
    expect(onError).toHaveBeenCalledOnce();
  });

  it('rejects a missing or detached print element before opening a print job', () => {
    expect(() => printSalesDocument(null, {})).toThrow('打印文档未准备好');
    expect(printJsMock).not.toHaveBeenCalled();
  });
});
