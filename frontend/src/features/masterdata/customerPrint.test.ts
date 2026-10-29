import { afterEach, describe, expect, it, vi } from 'vitest';
import { showPrintPreview } from '../../components/printing/printPreview';
import { printCustomerStatement, printSavedSalesOrder } from './customerPrint';
import type { Customer } from './types';
import type { SalesOrderDetail } from '../sales/types';

vi.mock('../../components/printing/printPreview', () => ({ showPrintPreview: vi.fn() }));
const customer = { customerName: '<script>alert(1)</script>', customerNo: 'CUS1', mobile: '13800138000', detailAddress: 'A&B' } as Customer;
afterEach(() => vi.clearAllMocks());

describe('customer printing', () => {
  it('prints the supplied complete statement and escapes customer and order text', () => {
    printCustomerStatement(customer, { records: [1, 2].map(id => ({ id, orderNo: `SO<${id}>`, orderDate: '2026-10-14', totalAmount: 10.1, receivedAmount: 2, outstandingAmount: 8.1 })),
      page: 1, pageSize: 1, total: 2, summary: { orderCount: 2, totalAmount: 20.2, receivedAmount: 4, outstandingAmount: 16.2 } }, '', '2026-10-14');
    const options = vi.mocked(showPrintPreview).mock.calls[0][0] as unknown as { printable: string; style: string };
    expect(options.printable).toContain('SO&lt;1&gt;');
    expect(options.printable).toContain('SO&lt;2&gt;');
    expect(options.printable).toContain('¥20.20');
    expect(options.printable).toContain('&lt;script&gt;');
    expect(options.printable).not.toContain('<script>');
    expect(options.printable).toContain('A&amp;B');
    expect(options.style).toContain('A4');
  });

  it('prints saved order identity and historical line snapshots', () => {
    printSavedSalesOrder({ orderNo: 'SO-SAVED-001', orderDate: '2026-10-14', customerName: '客户', deliveryAddress: '<img onerror="bad">', warehouseName: '仓库', salespersonMobile: '13800138000',
      goodsAmount: 16.5, discountAmount: 0, freight: 0, totalAmount: 16.5, receivedAmount: 0, outstandingAmount: 16.5, remark: 'A&B',
      items: [{ itemNoSnapshot: 'SKU-OLD', productNameSnapshot: '历史商品', skuNameSnapshot: '历史规格', quantity: 2, salesUnitSnapshot: '只', unitPrice: 8.25, amount: 16.5 }] } as SalesOrderDetail);
    const options = vi.mocked(showPrintPreview).mock.calls[0][0] as unknown as { printable: string };
    expect(options.printable).toContain('SO-SAVED-001');
    expect(options.printable).toContain('历史商品');
    expect(options.printable).toContain('8.25');
    expect(options.printable).not.toContain('<img');
  });
});
