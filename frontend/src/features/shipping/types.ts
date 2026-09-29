import type { PageResult } from '../masterdata/types';

export const SHIPMENT_STATUSES = [
  { value: 'unfinished', label: '未完成', tone: 'bg-slate-100 text-slate-600' },
  { value: 'completed', label: '已完成', tone: 'bg-emerald-50 text-emerald-700' },
  { value: 'out_of_stock', label: '缺货', tone: 'bg-rose-50 text-rose-600' },
  { value: 'partially_shipped', label: '部分发货', tone: 'bg-amber-50 text-amber-700' }
] as const;

export type ShipmentStatus = typeof SHIPMENT_STATUSES[number]['value'];
export type LogisticsOrderState = 'processing' | 'succeeded' | 'rejected' | 'unknown';

export interface RecipientFields {
  recipientName: string;
  recipientPhone: string;
  recipientProvince: string;
  recipientCity: string;
  recipientCounty: string;
  recipientDetailAddress: string;
}

export interface SenderFields {
  senderName: string;
  senderPhone: string;
  senderProvince: string;
  senderCity: string;
  senderCounty: string;
  senderDetailAddress: string;
}

export interface AneOrderDraft {
  cargoName: string;
  packType: string;
  weight: number | null;
  volume: number | null;
  pieceAmount: number | null;
  productTypeId: number | null;
  goodsType: number | null;
  payType: number | null;
  logisticsRemark: string;
}

export interface ShipmentFormInput extends RecipientFields, SenderFields {
  platformId?: number | null;
  shopId?: number | null;
  platform: string;
  shopName: string;
  preparers: string[];
  preparationContent: string;
  remark: string;
  estimatedFreight: number | null;
  orderDraft: AneOrderDraft;
}

export interface ShipmentContent extends ShipmentFormInput {
  shipmentDate: string;
  status: ShipmentStatus;
  orderer: string;
  logisticsCompany: string;
  trackingNo: string;
}

export interface Shipment {
  id: number;
  shipmentNo: string;
  logisticsOrderState: LogisticsOrderState | null;
  content: ShipmentContent;
  version: number;
  createdBy: string;
  updatedBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface ShipmentQuery {
  platformId?: number;
  shopId?: number;
  unlinkedOnly?: boolean;
  page: number;
  size: number;
  keyword?: string;
  status?: ShipmentStatus | '';
  dateFrom?: string;
  dateTo?: string;
  platform?: string;
  incompleteOnly?: boolean;
}

export interface ShipmentSummary {
  todayCount: number;
  unfinishedCount: number;
  completedCount: number;
  outOfStockCount: number;
  partiallyShippedCount: number;
}

export interface PreparerOption { employeeId: number; employeeName: string }
export interface ShippingFormOptions {
  shopNames: string[]; preparers: PreparerOption[];
  platforms: { id: number; name: string }[];
  shops: { id: number; platformId: number; name: string; optionLabel?: string }[];
}
export interface ShippingFilterOptions {
  platforms: { id: number; name: string; status: string }[];
  shops: { id: number; platformId: number; name: string; optionLabel?: string; status: string; platformStatus: string }[];
}
export interface SenderInfo {
  name: string;
  phone: string;
  province: string;
  city: string;
  county: string;
  address: string;
}
export interface LogisticsAvailability {
  available: boolean;
  testEnvironment: boolean;
  message: string;
  sender: SenderInfo | null;
}
export interface LogisticsOrder {
  shipmentId: number;
  orderNo: string;
  state: LogisticsOrderState;
  trackingNo: string;
  childTrackingNos: string;
  message: string;
  updatedAt: string;
  testEnvironment: boolean;
}
export interface PlaceLogisticsOrder { version: number }

export interface ShippingService {
  list(query: ShipmentQuery): Promise<PageResult<Shipment>>;
  summary(date: string): Promise<ShipmentSummary>;
  formOptions(): Promise<ShippingFormOptions>;
  getFilterOptions(): Promise<ShippingFilterOptions>;
  get(id: number): Promise<Shipment>;
  create(form: ShipmentFormInput): Promise<Shipment>;
  update(id: number, form: ShipmentFormInput, status: ShipmentStatus, version: number): Promise<Shipment>;
  logisticsAvailability(): Promise<LogisticsAvailability>;
  getLogisticsOrder(id: number): Promise<LogisticsOrder | null>;
  placeLogisticsOrder(id: number, request: PlaceLogisticsOrder): Promise<LogisticsOrder>;
}

export function todayDate() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
}

export function emptyShipmentForm(): ShipmentFormInput {
  return {
    platform: '', shopName: '', preparers: [], recipientName: '', recipientPhone: '',
    recipientProvince: '', recipientCity: '', recipientCounty: '', recipientDetailAddress: '',
    senderName: '', senderPhone: '', senderProvince: '', senderCity: '', senderCounty: '', senderDetailAddress: '',
    preparationContent: '', remark: '', estimatedFreight: null,
    orderDraft: { cargoName: '', packType: '纸箱', weight: null, volume: null, pieceAmount: 1,
      productTypeId: 524, goodsType: 180, payType: 102, logisticsRemark: '' }
  };
}

export function recipientFullAddress(content: RecipientFields) {
  return `${content.recipientProvince}${content.recipientCity}${content.recipientCounty}${content.recipientDetailAddress}`;
}

export function senderFullAddress(sender: SenderInfo) {
  return `${sender.province}${sender.city}${sender.county}${sender.address}`;
}

export function shipmentSenderFullAddress(sender: SenderFields) {
  return `${sender.senderProvince}${sender.senderCity}${sender.senderCounty}${sender.senderDetailAddress}`;
}

export function shipmentStatus(value: ShipmentStatus) {
  return SHIPMENT_STATUSES.find(item => item.value === value) ?? SHIPMENT_STATUSES[0];
}

export function logisticsStateLabel(value?: LogisticsOrderState) {
  return logisticsOrderStatus(value).label;
}

export function logisticsOrderStatus(value?: LogisticsOrderState | null) {
  if (!value) return { label: '尚未下单', tone: 'bg-slate-100 text-slate-600' };
  return ({
    processing: { label: '下单中', tone: 'bg-blue-50 text-blue-700' },
    succeeded: { label: '下单成功', tone: 'bg-emerald-50 text-emerald-700' },
    rejected: { label: '下单失败', tone: 'bg-rose-50 text-rose-600' },
    unknown: { label: '结果待核实', tone: 'bg-amber-50 text-amber-700' }
  } as const)[value];
}

export function freightText(value: number | null) { return value == null ? '未填写' : `¥${value.toFixed(2)}`; }
