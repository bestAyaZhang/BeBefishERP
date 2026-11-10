import { downloadCsv, escapeCsvCell, type CsvValue } from '../../services/csvDownload';
import { logisticsOrderStatus, recipientFullAddress, shipmentStatus, type Shipment } from './types';

function maskContact(value: string | null | undefined): string {
  const phone = value?.trim();
  if (!phone) return '';
  return /^\d{11}$/u.test(phone) ? `${phone.slice(0, 3)}****${phone.slice(-4)}` : '****';
}

const columns: { label: string; value: (shipment: Shipment) => CsvValue }[] = [
  { label: '发货单号', value: shipment => shipment.shipmentNo },
  { label: '发货日期', value: ({ content }) => content.shipmentDate },
  { label: '平台', value: ({ content }) => content.platform },
  { label: '店铺', value: ({ content }) => content.shopName },
  { label: '收件人', value: ({ content }) => content.recipientName },
  { label: '收件电话', value: ({ content }) => maskContact(content.recipientPhone) },
  { label: '收件地址', value: ({ content }) => recipientFullAddress(content) },
  { label: '备货清单', value: ({ content }) => content.preparationContent },
  { label: '备注', value: ({ content }) => content.remark },
  { label: '物流公司', value: ({ content }) => content.logisticsCompany },
  { label: '物流状态', value: ({ logisticsOrderState }) => logisticsOrderStatus(logisticsOrderState).label },
  { label: '运单号', value: ({ logisticsOrderState, content }) => logisticsOrderState === 'cancelled' ? '原运单已作废' : content.trackingNo },
  { label: '实际重量(kg)', value: ({ preparation }) => preparation?.actualWeight },
  { label: '下单重量(kg)', value: ({ content }) => content.orderDraft.weight },
  { label: '运费预测(元)', value: ({ content }) => content.estimatedFreight },
  { label: '备货状态', value: ({ content }) => shipmentStatus(content.status).label },
  { label: '备货人', value: ({ content }) => content.preparers.join('、') },
  { label: '下单人', value: ({ content }) => content.orderer }
];

export function buildShipmentCsv(records: Shipment[]): string {
  const rows = [columns.map(({ label }) => escapeCsvCell(label)).join(',')];
  for (const record of records) {
    rows.push(columns.map(({ value }) => escapeCsvCell(value(record))).join(','));
  }
  return `\uFEFF${rows.join('\r\n')}\r\n`;
}

export function downloadShipmentCsv(records: Shipment[], filename: string): void {
  downloadCsv(buildShipmentCsv(records), filename);
}
