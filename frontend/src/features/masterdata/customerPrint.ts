import { showPrintPreview } from '../../components/printing/printPreview';
import type { CustomerBills } from './customerAccountService';
import type { Customer } from './types';
import type { SalesOrderDetail } from '../sales/types';

export function escapePrintText(value: unknown) {
  return String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]!));
}
const money = (value: number) => Number(value).toFixed(2);
const style = `@page{size:A4 portrait;margin:14mm}body{color:#172033;font:12px Arial,"Microsoft YaHei",sans-serif}
  h1{text-align:center;font-size:24px}p{line-height:1.7;overflow-wrap:anywhere}table{width:100%;border-collapse:collapse;font-size:11px}
  th,td{border:1px solid #bfc8d6;padding:8px;text-align:left;overflow-wrap:anywhere}thead{display:table-header-group}
  tr{break-inside:avoid}.amount{text-align:right;white-space:nowrap}footer{margin-top:28px;display:flex;justify-content:space-between}
  .summary{font-weight:bold;line-height:2;break-inside:avoid}`;

export function printCustomerStatement(customer: Customer, bills: CustomerBills, from: string, to: string) {
  const e = escapePrintText;
  showPrintPreview({ type: 'raw-html', documentTitle: `${customer.customerName}－客户对账单`, scanStyles: false, style,
    printable: `<h1>客户对账单</h1><p>客户：${e(customer.customerName)}　编号：${e(customer.customerNo)}<br>
      电话：${e(customer.mobile || customer.telephone)}<br>地址：${e([customer.province, customer.city, customer.district, customer.detailAddress].filter(Boolean).join(''))}<br>
      开单日期：${e(from || '不限')} 至 ${e(to || '不限')}</p>
      <table><thead><tr><th>日期</th><th>销售单号</th><th>应收金额</th><th>已收金额</th><th>欠款金额</th></tr></thead>
      <tbody>${bills.records.map(o => `<tr><td>${e(o.orderDate)}</td><td>${e(o.orderNo)}</td><td class="amount">${money(o.totalAmount)}</td>
      <td class="amount">${money(o.receivedAmount)}</td><td class="amount">${money(o.outstandingAmount)}</td></tr>`).join('')}</tbody></table>
      <p class="summary">共 ${bills.summary.orderCount} 单　应收 ¥${money(bills.summary.totalAmount)}　已收 ¥${money(bills.summary.receivedAmount)}　欠款 ¥${money(bills.summary.outstandingAmount)}</p>
      <p>仅统计已确认销售单；已收金额为开单时登记的收款。</p><footer><span>经办人：____________</span><span>客户确认：____________</span></footer>` });
}

export function printSavedSalesOrder(order: SalesOrderDetail) {
  const e = escapePrintText;
  showPrintPreview({ type: 'raw-html', documentTitle: order.orderNo + '－出库送货单', scanStyles: false, style,
    printable: `<h1>出库送货单</h1><p>销售单号：${e(order.orderNo)}　日期：${e(order.orderDate)}<br>客户：${e(order.customerName)}<br>
      送货地址：${e(order.deliveryAddress)}<br>发货仓库：${e(order.warehouseName)}　业务代表：${e(order.salespersonMobile)}</p>
      <table><thead><tr><th>货号</th><th>商品 / 规格</th><th>数量</th><th>单位</th><th>单价</th><th>金额</th></tr></thead>
      <tbody>${order.items.map(i => `<tr><td>${e(i.itemNoSnapshot)}</td><td>${e(i.productNameSnapshot)}<br>${e(i.specificationSnapshot || i.skuNameSnapshot)}</td>
      <td>${e(i.quantity)}</td><td>${e(i.salesUnitSnapshot)}</td><td class="amount">${money(i.unitPrice)}</td><td class="amount">${money(i.amount)}</td></tr>`).join('')}</tbody></table>
      <p class="summary">商品 ¥${money(order.goodsAmount)}　折扣 ¥${money(order.discountAmount)}　运费 ¥${money(order.freight)}<br>
      应收 ¥${money(order.totalAmount)}　已收 ¥${money(order.receivedAmount)}　欠款 ¥${money(order.outstandingAmount)}</p>
      <p>备注：${e(order.remark || '无')}</p><footer><span>发货人：____________</span><span>客户签收：____________</span></footer>` });
}
