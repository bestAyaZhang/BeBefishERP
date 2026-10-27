import type { PageResult } from '../masterdata/types';

export const SHIPMENT_STATUSES = [
  { value: 'unfinished', label: '未完成', tone: 'bg-slate-100 text-slate-600' },
  { value: 'completed', label: '已完成', tone: 'bg-emerald-50 text-emerald-700' },
  { value: 'out_of_stock', label: '缺货', tone: 'bg-rose-50 text-rose-600' },
  { value: 'partially_shipped', label: '部分发货', tone: 'bg-amber-50 text-amber-700' }
] as const;

export type ShipmentStatus = typeof SHIPMENT_STATUSES[number]['value'];
export type LogisticsOrderState = 'processing' | 'succeeded' | 'rejected' | 'unknown'
  | 'cancel_processing' | 'cancel_rejected' | 'cancel_unknown' | 'cancelled';

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

export interface YimiOrderOptions { visitDelivery:number|null; deliveryFlag:number|null; floorNo:string }

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
  yimi?: YimiOrderOptions | null;
}

export interface ShipmentFormInput extends RecipientFields, SenderFields {
  logisticsChoice?: import('../logistics/types').LogisticsChoice;
  platformId?: number | null;
  shopId?: number | null;
  platform: string;
  shopName: string;
  preparers: string[];
  preparerEmployeeIds?: number[];
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
  creatorEmployeeId?: number | null;
  updatedBy: string;
  createdAt: string;
  updatedAt: string;
  preparerEmployeeIds?: number[];
  preparation?: { actualWeight: number | null; updatedBy: string | null; employeeId?: number | null; updatedAt: string | null };
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
  managedTemplatesEnabled?: boolean;
  available: boolean;
  testEnvironment: boolean;
  message: string;
  sender: SenderInfo | null;
}
export interface LogisticsOrder {
  provider?: 'ANE' | 'YIMI';
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
export interface LogisticsAccountBinding { companyId:number|null; accountId:number|null; accountVersionId:number|null; linked:boolean; requiresConfirmation:boolean }
export interface LogisticsTrace { status:'available'|'empty'|'unavailable'|'not_supported'; message:string; events:{time:string;status:string;location:string;description:string}[] }

export interface ShippingService {
  list(query: ShipmentQuery): Promise<PageResult<Shipment>>;
  summary(date: string): Promise<ShipmentSummary>;
  formOptions(): Promise<ShippingFormOptions>;
  getFilterOptions(): Promise<ShippingFilterOptions>;
  get(id: number): Promise<Shipment>;
  create(form: ShipmentFormInput): Promise<Shipment>;
  update(id: number, form: ShipmentFormInput, status: ShipmentStatus, version: number): Promise<Shipment>;
  updatePreparation(id: number, request: { status: ShipmentStatus; actualWeight: number | null; version: number }): Promise<Shipment>;
  updatePreparationContent(id: number, request: { preparationContent: string; remark: string; version: number }): Promise<Shipment>;
  confirmCreator(id: number, request: { employeeId: number; version: number }): Promise<Shipment>;
  logisticsAvailability(companyId?:number): Promise<LogisticsAvailability>;
  getLogisticsAccountBinding(id:number):Promise<LogisticsAccountBinding>;
  getLogisticsOrder(id: number): Promise<LogisticsOrder | null>;
  getLogisticsTrace(id: number): Promise<LogisticsTrace>;
  placeLogisticsOrder(id: number, request: PlaceLogisticsOrder): Promise<LogisticsOrder>;
  cancelLogisticsOrder(id: number, request: { version: number }): Promise<LogisticsOrder>;
}

export function todayDate() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
}

export function emptyShipmentForm(): ShipmentFormInput {
  return {
    platform: '', shopName: '', preparers: [], preparerEmployeeIds: [], recipientName: '', recipientPhone: '',
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
    unknown: { label: '结果待核实', tone: 'bg-amber-50 text-amber-700' },
    cancel_processing: { label: '取消中', tone: 'bg-blue-50 text-blue-700' },
    cancel_rejected: { label: '取消失败', tone: 'bg-rose-50 text-rose-600' },
    cancel_unknown: { label: '取消待核实', tone: 'bg-amber-50 text-amber-700' },
    cancelled: { label: '已取消', tone: 'bg-slate-100 text-slate-600' }
  } as const)[value];
}

export function freightText(value: number | null) { return value == null ? '未填写' : `¥${value.toFixed(2)}`; }

// Called only for API ordering: incomplete logistics details may still be saved as drafts.
export function validateYimiOrder(form: ShipmentFormInput, productCode: string): string {
  const draft=form.orderDraft, options=draft.yimi;
  if(options?.visitDelivery!==0 && options?.visitDelivery!==1)return '壹米下单前请明确选择上门揽货或自送';
  if(!Number.isInteger(draft.pieceAmount) || draft.pieceAmount==null || draft.pieceAmount<1 || draft.pieceAmount>1000)return '壹米件数须为1至1000的整数';
  for(const value of [draft.weight,draft.volume]){
    if(value==null)continue;
    if(!Number.isFinite(value) || value<0)return '壹米重量和体积不能小于零';
    if(Number(value.toFixed(2))!==value)return '壹米重量和体积最多两位小数';
  }
  if(form.senderDetailAddress.length>100)return '壹米寄件地址最多100字';
  if(form.recipientDetailAddress.length>50)return '壹米收件地址最多50字';
  if(form.senderPhone.length>20 || form.recipientPhone.length>20)return '壹米联系电话最多20字';
  if(draft.logisticsRemark.length>100)return '壹米物流备注最多100字';
  if(![102,104,103].includes(draft.payType??0))return '请选择现付、月结或到付';
  if(![180,179,285].includes(draft.goodsType??0))return '请选择送货方式';
  const expectedProduct=draft.goodsType===285?'2':'1';
  if(productCode!==expectedProduct)return `壹米${draft.goodsType===285?'自提须选择港到港':'送货须选择港到门'}模板`;
  if(draft.goodsType===179){
    if(options.deliveryFlag!==2 && options.deliveryFlag!==3)return '壹米送货上楼须明确选择有电梯或无电梯';
  }else if(options.deliveryFlag!=null && options.deliveryFlag!==(draft.goodsType===285?1:4))return '壹米送货方式与服务选项矛盾，请重新选择';
  if(options.floorNo && (options.deliveryFlag!==3 || !['-2','-1','2','3','4','5','6','7'].includes(options.floorNo)))return '无电梯楼层仅支持-2、-1、2至7层';
  return '';
}
