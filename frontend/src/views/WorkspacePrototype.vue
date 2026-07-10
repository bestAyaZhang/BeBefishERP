<script setup lang="ts">
import {
  ArrowDownRight,
  ArrowRight,
  ArrowUpRight,
  Bell,
  Box,
  CalendarDays,
  CircleUserRound,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  ClipboardList,
  Filter,
  LayoutDashboard,
  Menu,
  MessageCircle,
  MoreVertical,
  PackageCheck,
  PackageOpen,
  Plus,
  Search,
  Settings2,
  ShoppingCart,
  Truck,
  WalletCards,
  Warehouse,
  X
} from 'lucide-vue-next';
import { computed, ref, watch } from 'vue';

type SectionKey = 'dashboard' | 'products' | 'tasks' | 'calendar';
type ProductStatus = '全部状态' | '在售' | '待完善' | '低库存' | '已停用';
type ProductFilterKey = 'brand' | 'supplier' | 'category';
type ProductViewMode = 'list' | 'create';
type CreateProductField = {
  label: string;
  testId: string;
  placeholder: string;
  span?: string;
  type?: string;
  kind?: 'input' | 'upload' | 'select';
  options?: string[];
};
type CreateProductFormSection = {
  title: string;
  description: string;
  fields: CreateProductField[];
};
type TaskStage = 'Developing' | 'Designing' | 'Wireframe';

const activeSection = ref<SectionKey>('products');
const activeTab = ref('table');
const activeMenuTask = ref<string | null>('SKU-2026-001');
const showCalendarPicker = ref(false);
const searchQuery = ref('');
const activeChannel = ref('全部渠道');
const activeRange = ref('本周');
const activeProductStatus = ref<ProductStatus>('全部状态');
const selectedProductSku = ref('BBF-FEED-001');
const isProductDrawerOpen = ref(false);
const productViewMode = ref<ProductViewMode>('list');
const allProductBrandOption = '全部品牌';
const allProductSupplierOption = '全部供应商';
const allProductCategoryOption = '全部分类';
const productArticleNoQuery = ref('');
const selectedProductBrand = ref(allProductBrandOption);
const selectedProductSupplier = ref(allProductSupplierOption);
const selectedProductCategory = ref(allProductCategoryOption);
const activeProductFilterMenu = ref<ProductFilterKey | null>(null);
const activeCreateProductSelect = ref<string | null>(null);
const createProductSelectValues = ref<Record<string, string>>({});
const productPageSizes = [2, 10, 20, 50];
const productPageSize = ref(10);
const currentProductPage = ref(1);

const navItems = [
  { key: 'dashboard', label: '工作台', icon: LayoutDashboard },
  { key: 'products', label: '产品资料', icon: PackageOpen },
  { key: 'tasks', label: '任务清单', icon: ClipboardList },
  { key: 'inventory', label: '库存流水', icon: Warehouse },
  { key: 'purchase', label: '采购入库', icon: Truck },
  { key: 'sales', label: '销售开单', icon: ShoppingCart },
  { key: 'calendar', label: '日程排班', icon: CalendarDays },
  { key: 'messages', label: '消息中心', icon: MessageCircle }
];

const topicItems = [
  { label: '商品管理', color: 'text-amber-500', bg: 'bg-amber-50' },
  { label: '库存协同', color: 'text-sky-500', bg: 'bg-sky-50' },
  { label: '财务看板', color: 'text-rose-500', bg: 'bg-rose-50' }
];

const realtimeStats = [
  { label: '待付款', value: '639' },
  { label: '待发货', value: '320' },
  { label: '待售后', value: '178' },
  { label: '待评价', value: '245' },
  { label: '待处理投诉', value: '8' },
  { label: '违规商品', value: '13' }
];

const realtimePanels = [
  {
    label: '交易金额(元)',
    value: '2,434.23',
    change: '0.9%',
    trend: 'down',
    stroke: '#5b8bf7',
    fill: 'rgba(91, 139, 247, 0.12)',
    points: '0,44 18,43 32,42 44,34 58,37 72,23 86,21 100,11 114,22 128,19 142,26 156,25 170,42',
    area: '0,44 18,43 32,42 44,34 58,37 72,23 86,21 100,11 114,22 128,19 142,26 156,25 170,42 170,58 0,58'
  },
  {
    label: '订单数',
    value: '894',
    change: '0.9%',
    trend: 'up',
    stroke: '#63c7a7',
    fill: 'rgba(99, 199, 167, 0.14)',
    points: '0,40 16,41 32,44 48,32 64,28 80,18 96,12 112,19 128,22 144,15 160,29 176,31 190,42',
    area: '0,40 16,41 32,44 48,32 64,28 80,18 96,12 112,19 128,22 144,15 160,29 176,31 190,42 190,58 0,58'
  }
];

const projectCards = [
  { title: '夏季新品 SKU 建档', progress: 62, color: 'bg-[#536dff]', meta: ['2 评论', '5 附件', '7 天'] },
  { title: '1688 采购入库核对', progress: 87, color: 'bg-[#36bee3]', meta: ['4 评论', '3 附件', '2 天'] },
  { title: '线下订单开单模板', progress: 58, color: 'bg-[#7b45f5]', meta: ['2 评论', '8 附件', '8 天'] }
];

const pageHeaders: Record<SectionKey, { group: string; title: string }> = {
  dashboard: { group: 'Dashboards', title: 'Analytics' },
  products: { group: 'Products', title: 'Catalog' },
  tasks: { group: 'Tasks', title: 'Workspace' },
  calendar: { group: 'Calendar', title: 'Schedule' }
};

const currentPageHeader = computed(() => {
  if (activeSection.value === 'products' && productViewMode.value === 'create') return { group: 'Products', title: 'New Product' };
  return pageHeaders[activeSection.value];
});

const productStats = [
  { label: 'SKU 总数', value: '328', caption: '+18 本月新增', icon: PackageOpen, tone: 'bg-blue-50 text-[#536dff]' },
  { label: '在售商品', value: '286', caption: '87% 已上架', icon: PackageCheck, tone: 'bg-emerald-50 text-emerald-500' },
  { label: '库存预警', value: '14', caption: '低于安全线', icon: Warehouse, tone: 'bg-cyan-50 text-[#36bee3]' },
  { label: '待完善资料', value: '23', caption: '图片/条码缺失', icon: ClipboardList, tone: 'bg-amber-50 text-amber-500' }
];

const productStatuses: ProductStatus[] = ['全部状态', '在售', '待完善', '低库存', '已停用'];

const createProductSteps = ['基础信息', '渠道价格', '规格包装', '图片资料'];

const createProductFormSections: CreateProductFormSection[] = [
  {
    title: '基础信息',
    description: '先确定商品身份，便于后续入库、开单和渠道上架共用同一套 SKU。',
    fields: [
      { label: '产品图片', testId: 'create-product-image', placeholder: '上传产品主图，支持 JPG / PNG', span: 'md:col-span-2', kind: 'upload' },
      { label: '商品名称', testId: 'create-product-name', placeholder: '例如：静音增氧泵 Pro' },
      { label: 'SKU 编号', testId: 'create-product-sku', placeholder: '例如：BBF-PUMP-021' },
      { label: '货号', testId: 'create-product-article-no', placeholder: '例如：OP-PUMP-PRO-21' },
      { label: '规格', testId: 'create-product-spec', placeholder: '颜色 / 型号 / 功率 / 容量' },
      { label: '品牌', testId: 'create-product-brand', placeholder: '请选择品牌', kind: 'select', options: ['BeBefish', 'AquaLab', 'OceanPure'] },
      { label: '分类', testId: 'create-product-category', placeholder: '请选择分类', kind: 'select', options: ['鱼粮 / 组合装', '鱼缸 / 智能设备', '清洁 / 套装', '设备 / 增氧泵', '护理 / 水质'] }
    ]
  },
  {
    title: '渠道与价格',
    description: '记录渠道商、供应商和销售价格，方便后续按平台维护报价。',
    fields: [
      { label: '单价', testId: 'create-product-unit-price', placeholder: '例如：129.00', type: 'number' },
      { label: '成本价', testId: 'create-product-cost-price', placeholder: '例如：61.20', type: 'number' },
      { label: '供应商', testId: 'create-product-supplier', placeholder: '请选择供应商', kind: 'select', options: ['广州海悦宠物用品', '深圳蓝境科技', '宁波清氧电器', 'OceanPure 工厂'] },
      { label: '渠道商', testId: 'create-product-distributor', placeholder: '请选择渠道商', kind: 'select', options: ['1688', '抖音', '微信', '全部渠道', '线下经销商'] }
    ]
  },
  {
    title: '规格包装',
    description: '集中录入外箱、内盒和条码信息，减少采购入库与仓库复核时来回确认。',
    fields: [
      { label: '外箱长度', testId: 'create-product-outer-length', placeholder: '长 cm', type: 'number' },
      { label: '外箱宽度', testId: 'create-product-outer-width', placeholder: '宽 cm', type: 'number' },
      { label: '外箱高度', testId: 'create-product-outer-height', placeholder: '高 cm', type: 'number' },
      { label: '内盒包装', testId: 'create-product-inner-packaging', placeholder: '请选择内盒包装', kind: 'select', options: ['泡沫内托 + 彩盒', '彩盒 + 防潮袋', '吸塑托盘 + 彩卡', '牛皮盒 + 说明书', '自封袋 + 防潮盒'] },
      { label: '单杯条码', testId: 'create-product-cup-barcode', placeholder: '扫描或输入条码' },
      { label: '内盒长度', testId: 'create-product-inner-length', placeholder: '长 cm', type: 'number' },
      { label: '内盒宽度', testId: 'create-product-inner-width', placeholder: '宽 cm', type: 'number' },
      { label: '内盒高度', testId: 'create-product-inner-height', placeholder: '高 cm', type: 'number' },
      { label: '内盒重量', testId: 'create-product-inner-weight', placeholder: 'kg', type: 'number' }
    ]
  },
  {
    title: '图片与重量',
    description: '补充箱规图片和重量数据，支撑平台资料审核与物流计费。',
    fields: [
      { label: '外箱图片', testId: 'create-product-outer-box-image', placeholder: '上传外箱实拍或包装图', span: 'md:col-span-2', kind: 'upload' },
      { label: '内盒包装图', testId: 'create-product-inner-packaging-image', placeholder: '上传内盒包装图', span: 'md:col-span-2', kind: 'upload' },
      { label: '克重', testId: 'create-product-gram-weight', placeholder: '例如：500g' },
      { label: '净重', testId: 'create-product-net-weight', placeholder: 'kg', type: 'number' },
      { label: '毛重', testId: 'create-product-gross-weight', placeholder: 'kg', type: 'number' }
    ]
  }
];
const products = [
  {
    sku: 'BBF-FEED-001',
    name: '热带鱼粮组合装',
    spu: 'SPU-FISH-FOOD',
    category: '鱼粮 / 组合装',
    brand: 'BeBefish',
    channel: '1688',
    supplier: '广州海悦宠物用品',
    stock: 1260,
    safetyStock: 300,
    price: '¥49.90',
    cost: '¥21.30',
    status: '在售',
    audit: '资料完整',
    updated: '今天 09:24',
    sales: '2,436',
    imageTone: 'bg-blue-50 text-[#536dff]',
    alerts: ['库存健康', '多渠道同步', '已绑定条码']
  },
  {
    sku: 'BBF-TANK-009',
    name: '智能生态鱼缸 Mini',
    spu: 'SPU-AQUA-TANK',
    category: '鱼缸 / 智能设备',
    brand: 'AquaLab',
    channel: '抖音',
    supplier: '深圳蓝境科技',
    stock: 86,
    safetyStock: 120,
    price: '¥399.00',
    cost: '¥218.00',
    status: '低库存',
    audit: '待补主图',
    updated: '昨天 18:40',
    sales: '519',
    imageTone: 'bg-cyan-50 text-[#36bee3]',
    alerts: ['库存预警', '主图待补', '采购建议']
  },
  {
    sku: 'BBF-CLEAN-017',
    name: '鱼缸清洁护理套装',
    spu: 'SPU-CLEAN-KIT',
    category: '清洁 / 套装',
    brand: 'BeBefish',
    channel: '微信',
    supplier: '义乌水族日化',
    stock: 640,
    safetyStock: 180,
    price: '¥69.00',
    cost: '¥28.60',
    status: '在售',
    audit: '资料完整',
    updated: '03/18 14:12',
    sales: '864',
    imageTone: 'bg-emerald-50 text-emerald-500',
    alerts: ['利润稳定', '套装热卖', '可做加购']
  },
  {
    sku: 'BBF-PUMP-021',
    name: '静音增氧泵 Pro',
    spu: 'SPU-OXY-PUMP',
    category: '设备 / 增氧泵',
    brand: 'AquaLab',
    channel: '全部渠道',
    supplier: '宁波清氧电器',
    stock: 0,
    safetyStock: 80,
    price: '¥129.00',
    cost: '¥61.20',
    status: '已停用',
    audit: '待审核',
    updated: '03/12 11:30',
    sales: '312',
    imageTone: 'bg-slate-100 text-slate-500',
    alerts: ['已停用', '缺供应价', '待复核']
  },
  {
    sku: 'BBF-SALT-033',
    name: '水质调节海盐 500g',
    spu: 'SPU-WATER-SALT',
    category: '护理 / 水质',
    brand: 'OceanPure',
    channel: '1688',
    supplier: '厦门蓝湾生物',
    stock: 214,
    safetyStock: 260,
    price: '¥36.80',
    cost: '¥14.50',
    status: '待完善',
    audit: '缺少条码',
    updated: '03/10 16:02',
    sales: '1,124',
    imageTone: 'bg-violet-50 text-[#7b45f5]',
    alerts: ['缺少条码', '库存偏低', '待补规格']
  }
];

const initialProductSku = typeof window === 'undefined' ? null : new URLSearchParams(window.location.search).get('productSku');
if (initialProductSku && products.some((product) => product.sku === initialProductSku)) {
  selectedProductSku.value = initialProductSku;
  isProductDrawerOpen.value = true;
}

const channels = ['全部渠道', '微信', '抖音'];
const timeRanges = ['今日', '本周', '本月', '全年'];

const operationStats = [
  { label: '累计顾客数', value: '234', compare: '较昨日 120', change: '12%', tone: 'text-rose-500', trend: 'up' },
  { label: '累计商家数', value: '736', compare: '较昨日 120', change: '12%', tone: 'text-amber-500', trend: 'down' },
  { label: '访问次数(PV)', value: '231', compare: '较昨日 120', change: '12%', tone: 'text-rose-500', trend: 'up' },
  { label: '访问人数(UV)', value: '234', compare: '较昨日 120', change: '12%', tone: 'text-emerald-500', trend: 'down' }
];

const revenueTrend = [
  { month: '10月', value: 340, height: 34 },
  { month: '11月', value: 750, height: 75 },
  { month: '12月', value: 960, height: 96 },
  { month: '1月', value: 510, height: 51 },
  { month: '2月', value: 145, height: 14 },
  { month: '3月', value: 505, height: 50 },
  { month: '4月', value: 255, height: 26 },
  { month: '5月', value: 345, height: 35 },
  { month: '6月', value: 750, height: 75 },
  { month: '7月', value: 505, height: 50 },
  { month: '8月', value: 355, height: 36 },
  { month: '9月', value: 250, height: 25 }
];

const tasks = [
  { stage: 'Developing' as TaskStage, code: 'SKU-2026-001', name: '宠物鱼粮组合装建档', start: '14-Mar-24', due: '25-Mar-24', priority: 'High', status: 'Ongoing' },
  { stage: 'Developing' as TaskStage, code: 'SKU-2026-002', name: '财务经营看板字段确认', start: '10-Mar-24', due: '21-Mar-24', priority: 'Medium', status: 'Done' },
  { stage: 'Developing' as TaskStage, code: 'SKU-2026-003', name: 'App 页面素材整理', start: '07-Mar-24', due: '19-Mar-24', priority: 'High', status: 'Ongoing' },
  { stage: 'Designing' as TaskStage, code: 'PO-2026-006', name: '采购单确认流程原型', start: '14-Mar-24', due: '25-Mar-24', priority: 'Low', status: 'Ongoing' },
  { stage: 'Designing' as TaskStage, code: 'PO-2026-007', name: '供应商结算信息设计', start: '10-Mar-24', due: '21-Mar-24', priority: 'Medium', status: 'Done' },
  { stage: 'Designing' as TaskStage, code: 'SO-2026-010', name: '线下开单打印样式', start: '07-Mar-24', due: '19-Mar-24', priority: 'Low', status: 'Done' }
];

const filteredTasks = computed(() => {
  const query = searchQuery.value.trim().toLowerCase();
  if (!query) return tasks;
  return tasks.filter((task) => [task.code, task.name, task.stage, task.status].some((value) => value.toLowerCase().includes(query)));
});

const groupedTasks = computed(() =>
  (['Developing', 'Designing', 'Wireframe'] as TaskStage[]).map((stage) => ({
    stage,
    tasks: filteredTasks.value.filter((task) => task.stage === stage)
  }))
);

const filteredProducts = computed(() => {
  const query = searchQuery.value.trim().toLowerCase();
  const articleQuery = productArticleNoQuery.value.trim().toLowerCase();
  return products.filter((product) => {
    const details = productDetailFor(product.sku);
    const matchesStatus = activeProductStatus.value === '全部状态' || product.status === activeProductStatus.value;
    const matchesQuery = !query || [product.sku, product.name, product.spu, details.articleNo, product.category, product.brand, product.channel, product.supplier, product.status, product.audit].some((value) =>
      value.toLowerCase().includes(query)
    );
    const matchesArticle = !articleQuery || details.articleNo.toLowerCase().includes(articleQuery) || product.sku.toLowerCase().includes(articleQuery);
    const matchesBrand = selectedProductBrand.value === allProductBrandOption || product.brand === selectedProductBrand.value;
    const matchesSupplier = selectedProductSupplier.value === allProductSupplierOption || product.supplier === selectedProductSupplier.value;
    const matchesCategory = selectedProductCategory.value === allProductCategoryOption || product.category === selectedProductCategory.value;
    return matchesStatus && matchesQuery && matchesArticle && matchesBrand && matchesSupplier && matchesCategory;
  });
});
const productTotalPages = computed(() => Math.max(1, Math.ceil(filteredProducts.value.length / productPageSize.value)));
const productPaginationItems = computed<Array<number | 'ellipsis'>>(() => {
  const totalPages = productTotalPages.value;
  if (totalPages <= 7) return Array.from({ length: totalPages }, (_, index) => index + 1);
  return [1, 2, 3, 4, 5, 'ellipsis', totalPages];
});
const paginatedProducts = computed(() => {
  const startIndex = (currentProductPage.value - 1) * productPageSize.value;
  return filteredProducts.value.slice(startIndex, startIndex + productPageSize.value);
});


const selectedProduct = computed(() => products.find((product) => product.sku === selectedProductSku.value) ?? products[0]);

const productCompletionSegments = [
  { label: '基础字段', value: 94, color: 'bg-[#536dff]' },
  { label: '图片素材', value: 76, color: 'bg-[#36bee3]' },
  { label: '渠道价格', value: 88, color: 'bg-[#7b45f5]' },
  { label: '合规资料', value: 69, color: 'bg-[#fb7fa2]' }
];

const productDetailBySku = {
  'BBF-FEED-001': {
    articleNo: 'HF-FOOD-24A',
    spec: '12 杯/组 · 80g/杯',
    outerBoxSize: '48 x 36 x 32 cm',
    innerBoxSize: '16 x 12 x 10 cm',
    innerBoxWeight: '0.42 kg',
    innerPackaging: '彩盒 + 防潮袋',
    cupBarcode: '6973022400018',
    distributor: '1688 / 微信商城',
    gramWeight: '80g/杯',
    netWeight: '0.96 kg',
    grossWeight: '1.28 kg',
    productImage: 'feed-combo-main.jpg',
    outerBoxImage: 'feed-combo-carton.jpg',
    innerPackagingImage: 'feed-combo-inner-box.jpg'
  },
  'BBF-TANK-009': {
    articleNo: 'AQ-TANK-MINI-09',
    spec: 'Mini 款 · 白色 · 220V',
    outerBoxSize: '62 x 38 x 42 cm',
    innerBoxSize: '58 x 34 x 36 cm',
    innerBoxWeight: '5.60 kg',
    innerPackaging: '泡沫内托 + 彩盒',
    cupBarcode: '6973022400094',
    distributor: '抖音 / 1688 渠道商',
    gramWeight: '不适用',
    netWeight: '4.80 kg',
    grossWeight: '6.30 kg',
    productImage: 'tank-mini-product.jpg',
    outerBoxImage: 'tank-mini-outer-box.jpg',
    innerPackagingImage: 'tank-mini-inner-pack.jpg'
  },
  'BBF-CLEAN-017': {
    articleNo: 'CL-KIT-017',
    spec: '6 件套 · 标准款',
    outerBoxSize: '52 x 34 x 28 cm',
    innerBoxSize: '24 x 16 x 8 cm',
    innerBoxWeight: '0.75 kg',
    innerPackaging: '吸塑托盘 + 彩卡',
    cupBarcode: '6973022400179',
    distributor: '微信 / 社群渠道',
    gramWeight: '750g/套',
    netWeight: '0.72 kg',
    grossWeight: '0.95 kg',
    productImage: 'clean-kit-main.jpg',
    outerBoxImage: 'clean-kit-carton.jpg',
    innerPackagingImage: 'clean-kit-inner-pack.jpg'
  },
  'BBF-PUMP-021': {
    articleNo: 'OP-PUMP-PRO-21',
    spec: 'Pro 款 · 双孔静音',
    outerBoxSize: '46 x 32 x 30 cm',
    innerBoxSize: '18 x 12 x 9 cm',
    innerBoxWeight: '0.68 kg',
    innerPackaging: '牛皮盒 + 说明书',
    cupBarcode: '6973022400216',
    distributor: '线下经销商',
    gramWeight: '不适用',
    netWeight: '0.52 kg',
    grossWeight: '0.78 kg',
    productImage: 'oxygen-pump-main.jpg',
    outerBoxImage: 'oxygen-pump-carton.jpg',
    innerPackagingImage: 'oxygen-pump-inner-box.jpg'
  },
  'BBF-SALT-033': {
    articleNo: 'WS-SALT-500',
    spec: '500g/袋 · 海盐配方',
    outerBoxSize: '40 x 30 x 26 cm',
    innerBoxSize: '20 x 14 x 6 cm',
    innerBoxWeight: '0.58 kg',
    innerPackaging: '自封袋 + 防潮盒',
    cupBarcode: '6973022400339',
    distributor: '1688 渠道商',
    gramWeight: '500g/袋',
    netWeight: '0.50 kg',
    grossWeight: '0.62 kg',
    productImage: 'water-salt-main.jpg',
    outerBoxImage: 'water-salt-carton.jpg',
    innerPackagingImage: 'water-salt-inner-pack.jpg'
  }
};

function uniqueProductOptions(values: string[]) {
  return Array.from(new Set(values));
}

function productDetailFor(sku: string) {
  return productDetailBySku[sku as keyof typeof productDetailBySku] ?? productDetailBySku['BBF-FEED-001'];
}

const productBrandOptions = computed(() => [allProductBrandOption, ...uniqueProductOptions(products.map((product) => product.brand))]);
const productSupplierOptions = computed(() => [allProductSupplierOption, ...uniqueProductOptions(products.map((product) => product.supplier))]);
const productCategoryOptions = computed(() => [allProductCategoryOption, ...uniqueProductOptions(products.map((product) => product.category))]);

const selectedProductDetails = computed(() => productDetailFor(selectedProduct.value.sku));

watch([searchQuery, activeProductStatus, productArticleNoQuery, selectedProductBrand, selectedProductSupplier, selectedProductCategory], () => {
  currentProductPage.value = 1;
});

watch(productTotalPages, (totalPages) => {
  if (currentProductPage.value > totalPages) currentProductPage.value = totalPages;
});

function toggleProductFilterMenu(key: ProductFilterKey) {
  activeProductFilterMenu.value = activeProductFilterMenu.value === key ? null : key;
}

function selectProductFilter(key: ProductFilterKey, value: string) {
  if (key === 'brand') selectedProductBrand.value = value;
  if (key === 'supplier') selectedProductSupplier.value = value;
  if (key === 'category') selectedProductCategory.value = value;
  activeProductFilterMenu.value = null;
}

function resetProductFilters() {
  productArticleNoQuery.value = '';
  selectedProductBrand.value = allProductBrandOption;
  selectedProductSupplier.value = allProductSupplierOption;
  selectedProductCategory.value = allProductCategoryOption;
  activeProductFilterMenu.value = null;
}

function toggleCreateProductSelect(testId: string) {
  activeCreateProductSelect.value = activeCreateProductSelect.value === testId ? null : testId;
}

function selectCreateProductOption(testId: string, option: string) {
  createProductSelectValues.value = { ...createProductSelectValues.value, [testId]: option };
  activeCreateProductSelect.value = null;
}

function createProductSelectLabel(field: CreateProductField) {
  return createProductSelectValues.value[field.testId] ?? field.placeholder;
}

function setProductPage(page: number) {
  currentProductPage.value = Math.min(Math.max(page, 1), productTotalPages.value);
}

function goToPreviousProductPage() {
  setProductPage(currentProductPage.value - 1);
}

function goToNextProductPage() {
  setProductPage(currentProductPage.value + 1);
}

function updateProductPageSize(event: Event) {
  const nextPageSize = Number((event.target as HTMLSelectElement).value);
  productPageSize.value = nextPageSize;
  currentProductPage.value = 1;
}
function productStatusClass(status: string) {
  if (status === '在售') return 'bg-emerald-50 text-emerald-500';
  if (status === '低库存') return 'bg-cyan-50 text-[#36bee3]';
  if (status === '待完善') return 'bg-amber-50 text-amber-500';
  return 'bg-slate-100 text-slate-500';
}

function productAuditClass(audit: string) {
  if (audit === '资料完整') return 'bg-blue-50 text-[#536dff]';
  if (audit.includes('缺') || audit.includes('待补')) return 'bg-rose-50 text-rose-500';
  return 'bg-violet-50 text-[#7b45f5]';
}

function productStockRatio(stock: number, safetyStock: number) {
  return `${Math.min(100, Math.round((stock / Math.max(safetyStock * 2, 1)) * 100))}%`;
}

function openProductDrawer(sku: string) {
  selectedProductSku.value = sku;
  isProductDrawerOpen.value = true;
}

function closeProductDrawer() {
  isProductDrawerOpen.value = false;
}

function openProductCreateForm() {
  productViewMode.value = 'create';
  activeProductFilterMenu.value = null;
  activeCreateProductSelect.value = null;
  closeProductDrawer();
}

function closeProductCreateForm() {
  productViewMode.value = 'list';
  activeCreateProductSelect.value = null;
}

function selectNav(key: string) {
  if (key === 'dashboard' || key === 'products' || key === 'tasks' || key === 'calendar') {
    activeSection.value = key;
    productViewMode.value = 'list';
    activeCreateProductSelect.value = null;
    closeProductDrawer();
  }
}
</script>

<template>
  <main class="bebefish-prototype min-h-screen bg-[#f6f7fb] px-8 py-8 text-[#25314d]">
    <div class="mx-auto grid max-w-[1440px] grid-cols-[244px_minmax(0,1fr)] gap-6">
      <aside class="flex min-h-[calc(100vh-64px)] flex-col overflow-hidden rounded-[24px] border border-slate-200/80 bg-white shadow-[0_18px_50px_rgba(31,45,74,0.05)]">
        <div class="flex items-center gap-3 border-b border-slate-100 px-6 py-6">
          <div class="flex h-11 w-11 items-center justify-center rounded-full bg-[#536dff] text-white shadow-lg shadow-blue-200">
            <Box class="h-5 w-5" aria-hidden="true" />
          </div>
          <div class="min-w-0">
            <p class="truncate text-sm font-bold">BeBefish ERP</p>
            <p class="text-xs font-medium text-slate-400">电商经营管理</p>
          </div>
          <button class="ml-auto flex h-7 w-7 items-center justify-center rounded-lg border border-slate-200 text-slate-400 hover:bg-slate-50" type="button">
            <ChevronDown class="h-4 w-4" aria-hidden="true" />
          </button>
        </div>

        <nav class="flex-1 px-5 py-6">
          <p class="mb-4 px-2 text-xs font-semibold text-slate-400">Menu</p>
          <div class="space-y-1">
            <button
              v-for="item in navItems"
              :key="item.key"
              type="button"
              class="group relative flex h-11 w-full items-center gap-3 rounded-xl px-3 text-left text-sm font-semibold transition"
              :class="activeSection === item.key ? 'bg-[#f4f6ff] text-[#25314d]' : 'text-slate-400 hover:bg-slate-50 hover:text-[#25314d]'"
              @click="selectNav(item.key)"
            >
              <span v-if="activeSection === item.key" class="absolute -left-5 h-7 w-1 rounded-r-full bg-[#536dff]"></span>
              <component :is="item.icon" class="h-4 w-4" aria-hidden="true" />
              <span>{{ item.label }}</span>
              <ChevronRight v-if="activeSection === item.key" class="ml-auto h-4 w-4 text-slate-400" aria-hidden="true" />
            </button>
          </div>

          <p class="mb-4 mt-8 px-2 text-xs font-semibold text-slate-400">Topics</p>
          <div class="space-y-2">
            <button
              v-for="topic in topicItems"
              :key="topic.label"
              type="button"
              class="flex h-10 w-full items-center gap-3 rounded-xl px-3 text-sm font-semibold text-slate-400 hover:bg-slate-50"
            >
              <span class="flex h-5 w-5 items-center justify-center rounded-md" :class="[topic.bg, topic.color]">
                <PackageCheck class="h-3.5 w-3.5" aria-hidden="true" />
              </span>
              {{ topic.label }}
            </button>
          </div>
        </nav>

        <div class="mx-5 mb-5 flex items-center gap-3 border-t border-slate-100 pt-5">
          <div class="flex h-11 w-11 items-center justify-center rounded-full bg-blue-100 text-sm font-bold text-blue-600">AZ</div>
          <div class="min-w-0">
            <p class="truncate text-sm font-bold">Aya Zhang</p>
            <p class="truncate text-xs text-slate-400">admin@bebefish.cn</p>
          </div>
          <ChevronRight class="ml-auto h-4 w-4 text-slate-400" aria-hidden="true" />
        </div>
      </aside>

      <section class="min-w-0">
        <header class="mb-6 flex h-11 items-center justify-between gap-5">
          <div class="flex min-w-0 items-center gap-4">
            <button class="flex h-8 w-8 items-center justify-center rounded-md text-slate-500 transition hover:bg-white hover:text-[#25314d]" type="button" aria-label="Open navigation">
              <Menu class="h-5 w-5" aria-hidden="true" />
            </button>
            <nav class="flex items-center gap-2 text-sm" aria-label="Breadcrumb">
              <span class="font-medium text-slate-400">{{ currentPageHeader.group }}</span>
              <span class="text-slate-300">/</span>
              <span class="font-black text-[#0f172a]">{{ currentPageHeader.title }}</span>
            </nav>
          </div>

          <div class="ml-auto flex items-center gap-3">
            <label class="flex h-10 w-[220px] items-center rounded-md border border-slate-200 bg-white px-4 shadow-none">
              <input v-model="searchQuery" class="w-full border-0 bg-transparent text-sm font-medium outline-none placeholder:text-slate-400" placeholder="Search here" />
            </label>
            <button class="flex h-8 w-8 items-center justify-center rounded-full text-slate-500 transition hover:bg-white hover:text-[#25314d]" type="button" aria-label="Account">
              <CircleUserRound class="h-[18px] w-[18px]" aria-hidden="true" />
            </button>
            <button class="flex h-8 w-8 items-center justify-center rounded-full text-slate-500 transition hover:bg-white hover:text-[#25314d]" type="button" aria-label="Settings">
              <Settings2 class="h-[18px] w-[18px]" aria-hidden="true" />
            </button>
          </div>
        </header>

        <div v-if="activeSection === 'dashboard'" class="grid grid-cols-1 gap-6 min-[1180px]:grid-cols-[minmax(0,1fr)_360px] min-[1400px]:grid-cols-[minmax(0,1fr)_420px]">
          <div class="min-w-0 space-y-6">
            <article class="overflow-hidden rounded-[18px] border border-slate-200 bg-white shadow-[0_14px_40px_rgba(31,45,74,0.04)]">
              <div class="flex items-center justify-between border-b border-slate-100 px-5 py-4">
                <h2 class="text-lg font-black">实时数据</h2>
                <p class="text-xs font-bold text-slate-400">截止至 2022/06/30 12:00</p>
              </div>

              <div class="grid grid-cols-6 border-b border-slate-100 px-5 py-4">
                <section v-for="item in realtimeStats" :key="item.label" class="min-w-0 text-center">
                  <p class="text-2xl font-black leading-none tracking-tight text-[#25314d]">{{ item.value }}</p>
                  <p class="mt-2 truncate text-xs font-bold text-slate-500">{{ item.label }}</p>
                </section>
              </div>

              <div class="grid grid-cols-2 gap-5 px-5 py-4">
                <section v-for="panel in realtimePanels" :key="panel.label" class="min-w-0">
                  <p class="mb-2 text-xs font-bold text-slate-500">{{ panel.label }}</p>
                  <div class="grid grid-cols-[120px_minmax(0,1fr)] items-end gap-3">
                    <div>
                      <p class="text-3xl font-black leading-none tracking-tight text-[#25314d]">{{ panel.value }}</p>
                      <div class="mt-2 flex items-center gap-1 text-[11px] font-bold text-slate-400">
                        <span>较昨日</span>
                        <ArrowDownRight v-if="panel.trend === 'down'" class="h-3.5 w-3.5 text-emerald-500" aria-hidden="true" />
                        <ArrowUpRight v-else class="h-3.5 w-3.5 text-rose-500" aria-hidden="true" />
                        <span>{{ panel.change }}</span>
                      </div>
                    </div>
                    <div class="min-w-0">
                      <svg class="h-[74px] w-full" viewBox="0 0 190 74" preserveAspectRatio="none" aria-hidden="true">
                        <polygon :points="panel.area" :fill="panel.fill" />
                        <polyline :points="panel.points" fill="none" :stroke="panel.stroke" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" />
                      </svg>
                      <div class="-mt-1 flex justify-between text-[10px] font-bold text-slate-300">
                        <span>03/01</span>
                        <span>03/15</span>
                        <span>03/30</span>
                      </div>
                    </div>
                  </div>
                </section>
              </div>
            </article>

            <article class="overflow-hidden rounded-[18px] border border-slate-200 bg-white shadow-[0_14px_40px_rgba(31,45,74,0.04)]">
              <div class="flex flex-wrap items-center justify-between gap-4 border-b border-slate-100 px-5 py-4">
                <h2 class="text-lg font-black">运营数据</h2>
                <div class="inline-flex rounded-lg border border-slate-200 bg-white p-1">
                  <button
                    v-for="channel in channels"
                    :key="channel"
                    type="button"
                    class="h-8 rounded-md px-4 text-sm font-bold transition"
                    :class="activeChannel === channel ? 'bg-blue-50 text-[#536dff] ring-1 ring-[#536dff]' : 'text-slate-500 hover:bg-slate-50'"
                    @click="activeChannel = channel"
                  >
                    {{ channel }}
                  </button>
                </div>
              </div>

              <div class="grid grid-cols-4 gap-x-4 px-5 py-5">
                <section v-for="stat in operationStats" :key="stat.label" class="min-w-0">
                  <p class="text-xs font-bold text-slate-400">{{ stat.label }}</p>
                  <p class="mt-2 text-[26px] font-black leading-none tracking-tight text-[#25314d]">{{ stat.value }}</p>
                  <div class="mt-2 flex items-center gap-0.5 whitespace-nowrap text-[11px] font-bold text-slate-300">
                    <span>{{ stat.compare }}</span>
                    <ArrowUpRight v-if="stat.trend === 'up'" class="h-4 w-4" :class="stat.tone" aria-hidden="true" />
                    <ArrowDownRight v-else class="h-4 w-4" :class="stat.tone" aria-hidden="true" />
                    <span :class="stat.tone">{{ stat.change }}</span>
                  </div>
                </section>
              </div>

              <div class="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 px-5 pt-4">
                <h3 class="text-base font-black">销售额趋势</h3>
                <div class="flex flex-wrap items-center gap-3">
                  <div class="flex items-center gap-5 text-sm font-bold">
                    <button
                      v-for="range in timeRanges"
                      :key="range"
                      type="button"
                      class="transition"
                      :class="activeRange === range ? 'text-[#536dff]' : 'text-slate-500 hover:text-[#536dff]'"
                      @click="activeRange = range"
                    >
                      {{ range }}
                    </button>
                  </div>
                  <button class="inline-flex h-9 min-w-[190px] items-center justify-between rounded-lg border border-slate-200 px-3 text-xs font-semibold text-slate-300" type="button">
                    <span>请选择</span>
                    <ArrowRight class="h-4 w-4 text-slate-300" aria-hidden="true" />
                    <span>请选择</span>
                    <CalendarDays class="h-4 w-4 text-slate-300" aria-hidden="true" />
                  </button>
                </div>
              </div>

              <div class="px-5 pb-5 pt-4">
                <div class="grid h-[205px] grid-cols-[44px_1fr] gap-4">
                  <div class="flex flex-col justify-between pb-7 text-xs font-bold text-slate-400">
                    <span>1000</span>
                    <span>750</span>
                    <span>500</span>
                    <span>250</span>
                    <span>0</span>
                  </div>
                  <div class="relative overflow-hidden border-b border-slate-200">
                    <div class="absolute inset-x-0 top-1 bottom-7 flex flex-col justify-between">
                      <span v-for="line in 5" :key="line" class="border-t border-dashed border-slate-100"></span>
                    </div>
                    <div class="relative z-10 flex h-full items-end justify-between gap-2 pb-7 pr-1">
                      <div v-for="item in revenueTrend" :key="item.month" class="flex h-full min-w-0 flex-1 flex-col items-center justify-end gap-3">
                        <div class="w-full max-w-6 rounded-t-sm bg-[#2f6df6] shadow-[0_8px_18px_rgba(47,109,246,0.18)]" :style="{ height: `${item.height}%` }"></div>
                        <p class="whitespace-nowrap text-xs font-bold text-slate-400">{{ item.month }}</p>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </article>
            <div class="grid grid-cols-1 gap-6 min-[1400px]:grid-cols-2">
              <article class="rounded-[18px] border border-slate-200 bg-white p-6 shadow-[0_14px_40px_rgba(31,45,74,0.04)]">
                <div class="mb-5 flex items-center justify-between">
                  <h2 class="text-lg font-black">任务时间线</h2>
                  <button class="inline-flex h-9 items-center gap-2 rounded-lg border border-slate-200 px-3 text-xs font-bold text-slate-500" type="button">
                    Mar
                    <ChevronDown class="h-3.5 w-3.5" aria-hidden="true" />
                  </button>
                </div>
                <div class="relative h-[210px] overflow-hidden rounded-xl border border-slate-100 bg-[linear-gradient(90deg,#eef1f8_1px,transparent_1px)] bg-[size:20%_100%] p-5">
                  <div class="absolute left-[51%] top-8 h-[145px] w-0.5 bg-[#536dff]"></div>
                  <div class="absolute left-[49.5%] top-7 h-3 w-3 rotate-45 bg-[#536dff]"></div>
                  <div class="absolute left-5 top-8 h-8 w-36 rounded-lg border-l-4 border-[#536dff] bg-blue-50 px-3 py-1 text-[11px] font-bold">新品包装确认</div>
                  <div class="absolute left-[24%] top-[76px] h-8 w-36 rounded-lg border-l-4 border-[#36bee3] bg-cyan-50 px-3 py-1 text-[11px] font-bold">采购明细复核</div>
                  <div class="absolute left-[10%] top-[126px] h-8 w-36 rounded-lg border-l-4 border-[#7b45f5] bg-violet-50 px-3 py-1 text-[11px] font-bold">销售开单演示</div>
                </div>
              </article>

              <article class="rounded-[18px] border border-slate-200 bg-white p-6 shadow-[0_14px_40px_rgba(31,45,74,0.04)]">
                <div class="mb-5 flex items-center justify-between">
                  <h2 class="text-lg font-black">经营统计</h2>
                  <Settings2 class="h-5 w-5 text-slate-400" aria-hidden="true" />
                </div>
                <div class="flex items-center justify-center">
                  <div class="relative flex h-44 w-44 items-center justify-center rounded-full bg-[conic-gradient(#7b45f5_0_30%,#36bee3_30%_52%,#fb7fa2_52%_76%,#ffe1a8_76%_88%,#536dff_88%_100%)]">
                    <div class="flex h-28 w-28 flex-col items-center justify-center rounded-full bg-white">
                      <p class="text-3xl font-black">98</p>
                      <p class="text-xs font-semibold text-slate-400">Total Activity</p>
                    </div>
                  </div>
                </div>
                <div class="mt-5 flex justify-center gap-4 text-xs font-bold text-slate-500">
                  <span class="inline-flex items-center gap-1"><span class="h-2 w-2 rounded-full bg-[#536dff]"></span> SKU</span>
                  <span class="inline-flex items-center gap-1"><span class="h-2 w-2 rounded-full bg-[#7b45f5]"></span> 采购</span>
                  <span class="inline-flex items-center gap-1"><span class="h-2 w-2 rounded-full bg-[#36bee3]"></span> 销售</span>
                  <span class="inline-flex items-center gap-1"><span class="h-2 w-2 rounded-full bg-[#fb7fa2]"></span> 看板</span>
                </div>
              </article>
            </div>
          </div>

          <aside class="rounded-[22px] border border-slate-200 bg-white p-5 shadow-[0_14px_40px_rgba(31,45,74,0.04)]">
            <div class="mb-5 flex items-center justify-between">
              <h2 class="text-lg font-black">项目进度</h2>
              <button class="inline-flex h-9 items-center gap-2 rounded-lg border border-slate-200 px-3 text-xs font-bold text-slate-500" type="button">
                <CalendarDays class="h-3.5 w-3.5" aria-hidden="true" />
                Feb
                <ChevronDown class="h-3.5 w-3.5" aria-hidden="true" />
              </button>
            </div>
            <div class="mb-4 flex items-center justify-between text-sm font-black">
              <ChevronLeft class="h-4 w-4 text-slate-400" aria-hidden="true" />
              Sep 14, 2024
              <ChevronRight class="h-4 w-4 text-slate-400" aria-hidden="true" />
            </div>
            <div class="mb-5 grid grid-cols-5 gap-2">
              <button v-for="day in ['10 Sat', '11 Sun', '12 Mon', '13 Tue', '14 Wed']" :key="day" class="h-[72px] rounded-xl border border-slate-200 text-sm font-bold" :class="day.includes('12') ? 'bg-[#536dff] text-white shadow-lg shadow-blue-200' : 'bg-white text-slate-400'" type="button">
                <span class="block text-lg">{{ day.split(' ')[0] }}</span>
                <span class="text-xs">{{ day.split(' ')[1] }}</span>
              </button>
            </div>
            <div class="space-y-4">
              <article v-for="project in projectCards" :key="project.title" class="rounded-[18px] border border-slate-200 p-4">
                <div class="mb-3 flex items-start justify-between">
                  <span class="rounded-md bg-slate-50 px-2 py-1 text-[10px] font-bold text-slate-400">Project in progress</span>
                  <MoreVertical class="h-5 w-5 text-slate-400" aria-hidden="true" />
                </div>
                <h3 class="mb-3 text-base font-black leading-tight">{{ project.title }}</h3>
                <div class="mb-3 h-1.5 overflow-hidden rounded-full bg-slate-100">
                  <span class="block h-full rounded-full" :class="project.color" :style="{ width: `${project.progress}%` }"></span>
                </div>
                <div class="flex items-center">
                  <div class="flex -space-x-2">
                    <span class="flex h-8 w-8 items-center justify-center rounded-full border-2 border-white bg-blue-100 text-[10px] font-black text-blue-600">AZ</span>
                    <span class="flex h-8 w-8 items-center justify-center rounded-full border-2 border-white bg-rose-100 text-[10px] font-black text-rose-600">JJ</span>
                    <span class="flex h-8 w-8 items-center justify-center rounded-full border-2 border-white bg-cyan-100 text-[10px] font-black text-cyan-600">+2</span>
                  </div>
                  <div class="ml-auto flex gap-3 text-xs font-bold text-slate-500">
                    <span v-for="meta in project.meta" :key="meta">{{ meta }}</span>
                  </div>
                </div>
              </article>
            </div>
          </aside>
        </div>

        <div v-else-if="activeSection === 'products'" class="space-y-6">
          <template v-if="productViewMode === 'list'">
          <div class="min-w-0 space-y-6">
            <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 min-[1180px]:grid-cols-4">
              <article v-for="stat in productStats" :key="stat.label" class="rounded-[18px] border border-slate-200 bg-white p-5 shadow-[0_14px_40px_rgba(31,45,74,0.04)]">
                <div class="mb-4 flex items-center justify-between">
                  <span class="flex h-11 w-11 items-center justify-center rounded-full" :class="stat.tone">
                    <component :is="stat.icon" class="h-5 w-5" aria-hidden="true" />
                  </span>
                  <span class="rounded-full bg-slate-50 px-3 py-1 text-[11px] font-bold text-slate-400">{{ stat.caption }}</span>
                </div>
                <p class="text-sm font-bold text-slate-400">{{ stat.label }}</p>
                <p class="mt-2 text-3xl font-black leading-none tracking-tight text-[#25314d]">{{ stat.value }}</p>
              </article>
            </div>

            <article class="overflow-hidden rounded-[22px] border border-slate-200 bg-white shadow-[0_14px_40px_rgba(31,45,74,0.04)]">
              <div class="flex flex-wrap items-center justify-between gap-4 border-b border-slate-100 px-6 py-5">
                <div>
                  <h1 class="text-2xl font-black">SKU 主数据</h1>
                  <p class="mt-1 text-sm font-medium text-slate-400">{{ filteredProducts.length }} 个商品资料，按资料状态、库存风险和渠道快速维护。</p>
                </div>
                <div class="flex items-center gap-3">
                  <button class="inline-flex h-10 items-center gap-2 rounded-xl border border-slate-200 px-4 text-sm font-bold text-slate-600 hover:bg-slate-50" type="button">
                    <Filter class="h-4 w-4" aria-hidden="true" />
                    筛选
                  </button>
                  <button data-testid="add-product-button" class="inline-flex h-10 items-center gap-2 rounded-xl bg-[#536dff] px-4 text-sm font-bold text-white shadow-lg shadow-blue-200" type="button" @click="openProductCreateForm">
                    <Plus class="h-4 w-4" aria-hidden="true" />
                    新增商品
                  </button>
                </div>
              </div>

              <div class="border-b border-slate-100 bg-slate-50/45 px-6 py-4">
                <div class="grid gap-3 md:grid-cols-2 min-[1280px]:grid-cols-[1.1fr_0.9fr_1fr_1fr_auto]">
                  <label class="min-w-0">
                    <span class="mb-2 block text-xs font-black text-slate-400">货号</span>
                    <input
                      v-model="productArticleNoQuery"
                      data-testid="product-filter-article"
                      class="h-10 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm font-bold text-[#25314d] outline-none transition placeholder:text-slate-300 focus:border-[#536dff]"
                      placeholder="输入货号 / SKU"
                    />
                  </label>
                  <label class="relative min-w-0">
                    <span class="mb-2 block text-xs font-black text-slate-400">品牌</span>
                    <button
                      data-testid="product-filter-brand-button"
                      class="flex h-10 w-full items-center justify-between rounded-xl border border-slate-200 bg-white px-3 text-left text-sm font-bold text-[#25314d] shadow-sm shadow-slate-100 transition hover:border-[#536dff]/45 hover:bg-slate-50"
                      type="button"
                      @click="toggleProductFilterMenu('brand')"
                    >
                      <span class="truncate">{{ selectedProductBrand }}</span>
                      <ChevronDown class="h-4 w-4 text-slate-400" aria-hidden="true" />
                    </button>
                    <div v-if="activeProductFilterMenu === 'brand'" data-testid="product-filter-brand-menu" class="absolute left-0 right-0 top-[calc(100%+8px)] z-40 overflow-hidden rounded-xl border border-slate-200 bg-white p-1.5 shadow-[0_18px_42px_rgba(31,45,74,0.16)]">
                      <button
                        v-for="brand in productBrandOptions"
                        :key="brand"
                        :data-testid="brand === allProductBrandOption ? 'product-filter-brand-option-all' : brand === 'AquaLab' ? 'product-filter-brand-option-AquaLab' : 'product-filter-brand-option'"
                        class="flex h-9 w-full items-center rounded-lg px-3 text-left text-sm font-bold transition"
                        :class="selectedProductBrand === brand ? 'bg-blue-50 text-[#536dff]' : 'text-slate-600 hover:bg-slate-50 hover:text-[#25314d]'"
                        type="button"
                        @click="selectProductFilter('brand', brand)"
                      >
                        {{ brand }}
                      </button>
                    </div>
                  </label>
                  <label class="relative min-w-0">
                    <span class="mb-2 block text-xs font-black text-slate-400">供应商</span>
                    <button
                      data-testid="product-filter-supplier-button"
                      class="flex h-10 w-full items-center justify-between rounded-xl border border-slate-200 bg-white px-3 text-left text-sm font-bold text-[#25314d] shadow-sm shadow-slate-100 transition hover:border-[#536dff]/45 hover:bg-slate-50"
                      type="button"
                      @click="toggleProductFilterMenu('supplier')"
                    >
                      <span class="truncate">{{ selectedProductSupplier }}</span>
                      <ChevronDown class="h-4 w-4 text-slate-400" aria-hidden="true" />
                    </button>
                    <div v-if="activeProductFilterMenu === 'supplier'" data-testid="product-filter-supplier-menu" class="absolute left-0 right-0 top-[calc(100%+8px)] z-40 max-h-56 overflow-y-auto rounded-xl border border-slate-200 bg-white p-1.5 shadow-[0_18px_42px_rgba(31,45,74,0.16)]">
                      <button
                        v-for="supplier in productSupplierOptions"
                        :key="supplier"
                        :data-testid="supplier === allProductSupplierOption ? 'product-filter-supplier-option-all' : 'product-filter-supplier-option'"
                        class="flex h-9 w-full items-center rounded-lg px-3 text-left text-sm font-bold transition"
                        :class="selectedProductSupplier === supplier ? 'bg-blue-50 text-[#536dff]' : 'text-slate-600 hover:bg-slate-50 hover:text-[#25314d]'"
                        type="button"
                        @click="selectProductFilter('supplier', supplier)"
                      >
                        {{ supplier }}
                      </button>
                    </div>
                  </label>
                  <label class="relative min-w-0">
                    <span class="mb-2 block text-xs font-black text-slate-400">分类</span>
                    <button
                      data-testid="product-filter-category-button"
                      class="flex h-10 w-full items-center justify-between rounded-xl border border-slate-200 bg-white px-3 text-left text-sm font-bold text-[#25314d] shadow-sm shadow-slate-100 transition hover:border-[#536dff]/45 hover:bg-slate-50"
                      type="button"
                      @click="toggleProductFilterMenu('category')"
                    >
                      <span class="truncate">{{ selectedProductCategory }}</span>
                      <ChevronDown class="h-4 w-4 text-slate-400" aria-hidden="true" />
                    </button>
                    <div v-if="activeProductFilterMenu === 'category'" data-testid="product-filter-category-menu" class="absolute left-0 right-0 top-[calc(100%+8px)] z-40 max-h-56 overflow-y-auto rounded-xl border border-slate-200 bg-white p-1.5 shadow-[0_18px_42px_rgba(31,45,74,0.16)]">
                      <button
                        v-for="category in productCategoryOptions"
                        :key="category"
                        :data-testid="category === allProductCategoryOption ? 'product-filter-category-option-all' : 'product-filter-category-option'"
                        class="flex h-9 w-full items-center rounded-lg px-3 text-left text-sm font-bold transition"
                        :class="selectedProductCategory === category ? 'bg-blue-50 text-[#536dff]' : 'text-slate-600 hover:bg-slate-50 hover:text-[#25314d]'"
                        type="button"
                        @click="selectProductFilter('category', category)"
                      >
                        {{ category }}
                      </button>
                    </div>
                  </label>
                  <button class="mt-6 h-10 rounded-xl border border-slate-200 px-4 text-sm font-black text-slate-500 transition hover:bg-white hover:text-[#25314d]" type="button" @click="resetProductFilters">
                    重置
                  </button>
                </div>
              </div>

              <div class="flex flex-wrap items-center justify-between gap-4 px-6 py-4">
                <div class="flex flex-wrap gap-2">
                  <button
                    v-for="status in productStatuses"
                    :key="status"
                    type="button"
                    class="h-9 rounded-xl px-4 text-sm font-bold transition"
                    :class="activeProductStatus === status ? 'bg-[#536dff] text-white shadow-md shadow-blue-100' : 'border border-slate-200 text-slate-400 hover:bg-slate-50 hover:text-[#25314d]'"
                    @click="activeProductStatus = status"
                  >
                    {{ status }}
                  </button>
                </div>
                <div class="inline-flex items-center gap-2 rounded-xl bg-slate-50 px-3 py-2 text-xs font-bold text-slate-400">
                  <PackageCheck class="h-4 w-4 text-[#536dff]" aria-hidden="true" />
                  资料完整率 86%
                </div>
              </div>

              <div class="overflow-x-auto px-6 pb-6">
                <div class="min-w-[900px] overflow-hidden rounded-2xl border border-slate-100">
                  <div class="grid grid-cols-[1.35fr_0.9fr_0.65fr_0.65fr_0.7fr_0.6fr] bg-slate-50 px-5 py-3 text-xs font-black text-slate-400">
                    <span>商品信息</span>
                    <span>类目 / 品牌</span>
                    <span>库存</span>
                    <span>价格</span>
                    <span>资料状态</span>
                    <span class="text-right">更新</span>
                  </div>

                  <div v-if="filteredProducts.length" class="divide-y divide-slate-100">
                    <div
                      v-for="product in paginatedProducts"
                      :key="product.sku"
                      class="grid min-h-[76px] grid-cols-[1.35fr_0.9fr_0.65fr_0.65fr_0.7fr_0.6fr] items-center px-5 text-sm transition hover:bg-slate-50"
                      :class="selectedProductSku === product.sku ? 'bg-blue-50/45' : 'bg-white'"
                    >
                      <button class="flex min-w-0 items-center gap-3 text-left" type="button" @click="openProductDrawer(product.sku)">
                        <span class="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl font-black" :class="product.imageTone">
                          <PackageOpen class="h-5 w-5" aria-hidden="true" />
                        </span>
                        <span class="min-w-0">
                          <span class="block truncate font-black text-[#25314d]">{{ product.name }}</span>
                          <span class="mt-1 block truncate text-xs font-bold text-slate-400">{{ product.sku }} · {{ productDetailFor(product.sku).articleNo }} · {{ product.spu }}</span>
                        </span>
                      </button>
                      <div class="min-w-0">
                        <p class="truncate font-bold text-[#25314d]">{{ product.category }}</p>
                        <p class="mt-1 text-xs font-bold text-slate-400">{{ product.brand }} · {{ product.channel }}</p>
                      </div>
                      <div>
                        <p class="font-black text-[#25314d]">{{ product.stock }}</p>
                        <div class="mt-2 h-1.5 w-20 overflow-hidden rounded-full bg-slate-100">
                          <span class="block h-full rounded-full" :class="product.stock < product.safetyStock ? 'bg-[#36bee3]' : 'bg-[#536dff]'" :style="{ width: productStockRatio(product.stock, product.safetyStock) }"></span>
                        </div>
                      </div>
                      <div>
                        <p class="font-black text-[#25314d]">{{ product.price }}</p>
                        <p class="mt-1 text-xs font-bold text-slate-400">成本 {{ product.cost }}</p>
                      </div>
                      <div class="space-y-2">
                        <span class="inline-flex rounded-lg px-3 py-1 text-xs font-black" :class="productStatusClass(product.status)">{{ product.status }}</span>
                        <span class="block w-fit rounded-lg px-3 py-1 text-xs font-black" :class="productAuditClass(product.audit)">{{ product.audit }}</span>
                      </div>
                      <div class="text-right">
                        <p class="text-xs font-bold text-slate-400">{{ product.updated }}</p>

                      </div>
                    </div>
                  </div>

                  <div v-else class="flex h-40 items-center justify-center text-sm font-bold text-slate-400">
                    暂无匹配商品
                  </div>
                </div>

                <div data-testid="product-pagination" class="mt-4 flex min-w-[900px] flex-wrap items-center justify-end gap-2 rounded-2xl border border-slate-100 bg-white px-4 py-3 text-sm font-bold text-slate-500">
                  <span class="mr-auto whitespace-nowrap text-slate-600">&#20849;{{ filteredProducts.length }}&#26465;</span>
                  <button
                    data-testid="product-page-prev"
                    class="flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200 transition enabled:hover:border-[#536dff] enabled:hover:text-[#536dff] disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-300"
                    type="button"
                    aria-label="Previous page"
                    :disabled="currentProductPage === 1"
                    @click="goToPreviousProductPage"
                  >
                    <ChevronLeft class="h-4 w-4" aria-hidden="true" />
                  </button>
                  <template v-for="(item, index) in productPaginationItems" :key="`${item}-${index}`">
                    <span v-if="item === 'ellipsis'" class="flex h-8 min-w-8 items-center justify-center px-1 text-slate-400">...</span>
                    <button
                      v-else
                      data-testid="product-page-number"
                      class="flex h-8 min-w-8 items-center justify-center rounded-lg border px-2 transition"
                      :class="currentProductPage === item ? 'border-[#536dff] bg-blue-50 text-[#536dff] shadow-sm shadow-blue-100' : 'border-slate-200 bg-white text-slate-600 hover:border-[#536dff] hover:text-[#536dff]'"
                      type="button"
                      :aria-current="currentProductPage === item ? 'page' : undefined"
                      @click="setProductPage(Number(item))"
                    >
                      {{ item }}
                    </button>
                  </template>
                  <button
                    data-testid="product-page-next"
                    class="flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200 transition enabled:hover:border-[#536dff] enabled:hover:text-[#536dff] disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-300"
                    type="button"
                    aria-label="Next page"
                    :disabled="currentProductPage === productTotalPages"
                    @click="goToNextProductPage"
                  >
                    <ChevronRight class="h-4 w-4" aria-hidden="true" />
                  </button>
                  <label class="relative ml-2 inline-flex h-8 items-center rounded-lg border border-slate-200 bg-white pl-3 pr-2 text-slate-600 transition hover:border-[#536dff]">
                    <select data-testid="product-page-size" class="h-full appearance-none bg-transparent pr-7 text-sm font-bold outline-none" :value="productPageSize" @change="updateProductPageSize">
                      <option v-for="size in productPageSizes" :key="size" :value="size">{{ size }} &#26465;/&#39029;</option>
                    </select>
                    <ChevronDown class="pointer-events-none absolute right-2 h-4 w-4 text-slate-400" aria-hidden="true" />
                  </label>
                </div>
              </div>
            </article>
          </div>
          </template>

          <div v-else data-testid="product-create-form" class="space-y-6">
            <article class="overflow-hidden rounded-[22px] border border-slate-200 bg-white shadow-[0_14px_40px_rgba(31,45,74,0.04)]">
              <div class="flex flex-wrap items-center justify-between gap-4 border-b border-slate-100 px-6 py-5">
                <div class="flex min-w-0 items-center gap-4">
                  <button data-testid="back-to-product-list" class="inline-flex h-10 items-center gap-2 rounded-xl border border-slate-200 px-3 text-sm font-black text-slate-500 transition hover:bg-slate-50 hover:text-[#25314d]" type="button" @click="closeProductCreateForm">
                    <ChevronLeft class="h-4 w-4" aria-hidden="true" />
                    返回列表
                  </button>
                  <div class="min-w-0">
                    <p class="text-xs font-black uppercase text-[#536dff]">Product master data</p>
                    <h1 class="mt-1 text-2xl font-black">新增商品</h1>
                    <p class="mt-1 text-sm font-medium text-slate-400">补全 SKU、包装、价格、渠道和图片资料，提交后进入资料审核。</p>
                  </div>
                </div>
                <div class="flex items-center gap-3">
                  <button class="h-10 rounded-xl border border-slate-200 px-4 text-sm font-black text-slate-500 transition hover:bg-slate-50" type="button">保存草稿</button>
                  <button class="inline-flex h-10 items-center gap-2 rounded-xl bg-[#536dff] px-4 text-sm font-black text-white shadow-lg shadow-blue-200" type="button">
                    <PackageCheck class="h-4 w-4" aria-hidden="true" />
                    提交审核
                  </button>
                </div>
              </div>

              <div class="grid gap-3 border-b border-slate-100 bg-slate-50/45 px-6 py-4 sm:grid-cols-2 xl:grid-cols-4">
                <div v-for="(step, index) in createProductSteps" :key="step" class="flex min-h-16 items-center gap-3 rounded-2xl border border-slate-100 bg-white px-4 py-3">
                  <span class="flex h-8 w-8 shrink-0 items-center justify-center rounded-xl text-sm font-black" :class="index === 0 ? 'bg-[#536dff] text-white' : 'bg-slate-50 text-slate-400'">{{ index + 1 }}</span>
                  <span>
                    <span class="block text-sm font-black text-[#25314d]">{{ step }}</span>
                    <span class="mt-0.5 block text-xs font-bold text-slate-400">{{ index === 0 ? '当前填写' : '待完善' }}</span>
                  </span>
                </div>
              </div>
            </article>

            <div class="grid gap-6 xl:grid-cols-[minmax(0,1fr)_320px]">
              <form class="rounded-[22px] border border-slate-200 bg-white px-6 py-6 shadow-[0_14px_40px_rgba(31,45,74,0.04)]" @submit.prevent>
                <div class="space-y-8">
                  <section v-for="section in createProductFormSections" :key="section.title" class="border-b border-slate-100 pb-7 last:border-b-0 last:pb-0">
                    <div class="mb-5 flex flex-wrap items-start justify-between gap-3">
                      <div>
                        <h2 class="text-lg font-black text-[#25314d]">{{ section.title }}</h2>
                        <p class="mt-1 max-w-2xl text-sm font-medium text-slate-400">{{ section.description }}</p>
                      </div>
                      <span class="rounded-full bg-blue-50 px-3 py-1 text-xs font-black text-[#536dff]">必填资料</span>
                    </div>
                    <div class="grid gap-4 md:grid-cols-2">
                      <div v-for="field in section.fields" :key="field.testId" class="min-w-0" :class="field.span">
                        <span class="mb-2 block text-xs font-black text-slate-400">{{ field.label }}</span>
                        <label v-if="field.kind === 'upload'" class="group flex min-h-[112px] cursor-pointer items-center gap-4 rounded-2xl border border-dashed border-blue-200 bg-blue-50/45 px-4 py-4 transition hover:border-[#536dff] hover:bg-blue-50">
                          <input :data-testid="field.testId" class="sr-only" type="file" accept="image/*" />
                          <span class="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-white text-[#536dff] shadow-sm shadow-blue-100">
                            <Plus class="h-5 w-5" aria-hidden="true" />
                          </span>
                          <span class="min-w-0">
                            <span class="block text-sm font-black text-[#25314d]">点击上传图片</span>
                            <span class="mt-1 block truncate text-xs font-bold text-slate-400">{{ field.placeholder }}</span>
                          </span>
                        </label>
                        <div v-else-if="field.kind === 'select'" class="relative">
                          <button
                            :data-testid="field.testId"
                            class="flex h-11 w-full items-center justify-between gap-3 rounded-xl border border-slate-200 bg-white px-3 text-left text-sm font-bold text-[#25314d] shadow-sm shadow-slate-100 transition hover:border-[#536dff]/45 hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-blue-50"
                            type="button"
                            aria-haspopup="listbox"
                            :aria-expanded="activeCreateProductSelect === field.testId"
                            :aria-controls="`${field.testId}-menu`"
                            @click="toggleCreateProductSelect(field.testId)"
                          >
                            <span class="truncate" :class="createProductSelectValues[field.testId] ? 'text-[#25314d]' : 'text-slate-400'">
                              {{ createProductSelectLabel(field) }}
                            </span>
                            <ChevronDown class="h-4 w-4 shrink-0 text-slate-400" aria-hidden="true" />
                          </button>
                          <div
                            v-if="activeCreateProductSelect === field.testId"
                            :id="`${field.testId}-menu`"
                            :data-testid="`${field.testId}-menu`"
                            class="absolute left-0 right-0 top-[calc(100%+8px)] z-40 max-h-56 overflow-y-auto rounded-xl border border-slate-200 bg-white p-1.5 shadow-[0_18px_42px_rgba(31,45,74,0.16)]"
                            role="listbox"
                          >
                            <button
                              v-for="option in field.options ?? []"
                              :key="option"
                              :data-testid="`${field.testId}-option`"
                              class="flex min-h-9 w-full items-center rounded-lg px-3 py-2 text-left text-sm font-bold transition"
                              :class="createProductSelectValues[field.testId] === option ? 'bg-blue-50 text-[#536dff]' : 'text-slate-600 hover:bg-slate-50 hover:text-[#25314d]'"
                              type="button"
                              role="option"
                              :aria-selected="createProductSelectValues[field.testId] === option"
                              @click="selectCreateProductOption(field.testId, option)"
                            >
                              {{ option }}
                            </button>
                          </div>
                        </div>
                        <input
                          v-else
                          :data-testid="field.testId"
                          class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm font-bold text-[#25314d] outline-none transition placeholder:text-slate-300 focus:border-[#536dff] focus:ring-4 focus:ring-blue-50"
                          :placeholder="field.placeholder"
                          :type="field.type ?? 'text'"
                        />
                      </div>
                    </div>
                  </section>
                </div>
              </form>

              <aside class="h-fit rounded-[22px] border border-slate-200 bg-white p-6 shadow-[0_14px_40px_rgba(31,45,74,0.04)]">
                <div class="mb-5 flex items-center justify-between">
                  <h2 class="text-lg font-black">资料预检</h2>
                  <span class="rounded-full bg-amber-50 px-3 py-1 text-xs font-black text-amber-500">草稿</span>
                </div>
                <div class="space-y-4">
                  <div class="rounded-2xl border border-dashed border-blue-200 bg-blue-50/50 p-4">
                    <PackageOpen class="mb-3 h-7 w-7 text-[#536dff]" aria-hidden="true" />
                    <p class="text-sm font-black text-[#25314d]">主图与包装图</p>
                    <p class="mt-1 text-xs font-bold leading-5 text-slate-400">建议先补产品图片、外箱图片和内盒包装图，便于平台资料审核。</p>
                  </div>
                  <div class="space-y-3 text-sm font-bold text-slate-500">
                    <div class="flex items-center justify-between"><span>基础字段</span><span class="text-[#536dff]">0/8</span></div>
                    <div class="h-2 overflow-hidden rounded-full bg-slate-100"><span class="block h-full w-1/5 rounded-full bg-[#536dff]"></span></div>
                    <div class="flex items-center justify-between"><span>包装规格</span><span class="text-[#36bee3]">0/9</span></div>
                    <div class="h-2 overflow-hidden rounded-full bg-slate-100"><span class="block h-full w-1/6 rounded-full bg-[#36bee3]"></span></div>
                    <div class="flex items-center justify-between"><span>图片资料</span><span class="text-rose-500">0/3</span></div>
                    <div class="h-2 overflow-hidden rounded-full bg-slate-100"><span class="block h-full w-1/12 rounded-full bg-rose-400"></span></div>
                  </div>
                  <button class="mt-2 h-10 w-full rounded-xl border border-slate-200 text-sm font-black text-slate-500 transition hover:bg-slate-50" type="button">查看填写规范</button>
                </div>
              </aside>
            </div>
          </div>

        </div>
        <div v-else class="rounded-[22px] border border-slate-200 bg-white p-7 shadow-[0_14px_40px_rgba(31,45,74,0.04)]">
          <div class="mb-6 flex items-center justify-between">
            <div>
              <h1 class="text-2xl font-black">业务任务清单</h1>
              <p class="mt-1 text-sm font-medium text-slate-400">阶段 1 原型：用同一套布局承载产品、库存、采购和销售任务。</p>
            </div>
            <button class="inline-flex h-11 items-center gap-2 rounded-xl border border-slate-200 px-4 text-sm font-bold text-slate-600 hover:bg-slate-50" type="button">
              <Filter class="h-4 w-4" aria-hidden="true" />
              Filter
            </button>
          </div>

          <div class="mb-5 flex items-center gap-3">
            <button v-for="tab in ['table', 'board', 'calendar']" :key="tab" type="button" class="h-8 rounded-lg px-4 text-sm font-bold capitalize" :class="activeTab === tab ? 'bg-[#536dff] text-white' : 'border border-slate-200 text-slate-400'" @click="activeTab = tab">
              {{ tab }}
            </button>
          </div>

          <div class="space-y-5">
            <section v-for="group in groupedTasks" :key="group.stage" class="relative">
              <button class="mb-3 flex h-9 w-full items-center justify-between rounded-lg border px-4 text-sm font-black" :class="group.stage === 'Developing' ? 'border-blue-200 bg-blue-50 text-[#536dff]' : group.stage === 'Designing' ? 'border-violet-200 bg-violet-50 text-[#7b45f5]' : 'border-cyan-200 bg-cyan-50 text-[#36bee3]'" type="button">
                <span class="inline-flex items-center gap-2"><span class="h-2 w-2 rounded-full" :class="group.stage === 'Developing' ? 'bg-[#536dff]' : group.stage === 'Designing' ? 'bg-[#7b45f5]' : 'bg-[#36bee3]'"></span>{{ group.stage }}</span>
                <Plus class="h-4 w-4" aria-hidden="true" />
              </button>

              <div class="grid grid-cols-[1.2fr_0.65fr_0.65fr_0.55fr_0.55fr] px-5 py-2 text-xs font-black text-slate-400">
                <span>Name</span>
                <span>Start Date</span>
                <span>Due Date</span>
                <span>Priority</span>
                <span class="text-right">Status</span>
              </div>

              <div v-if="group.tasks.length" class="divide-y divide-slate-100">
                <div v-for="task in group.tasks" :key="task.code" class="relative grid min-h-[58px] grid-cols-[1.2fr_0.65fr_0.65fr_0.55fr_0.55fr] items-center px-5 text-sm font-bold">
                  <button class="text-left text-[#25314d]" type="button" @click="activeMenuTask = activeMenuTask === task.code ? null : task.code">{{ task.name }}</button>
                  <span class="text-slate-400">{{ task.start }}</span>
                  <button class="text-left font-black text-[#25314d]" type="button" @click="showCalendarPicker = !showCalendarPicker">{{ task.due }}</button>
                  <span>
                    <span class="rounded-lg px-3 py-1 text-xs" :class="task.priority === 'High' ? 'bg-blue-50 text-[#536dff]' : task.priority === 'Medium' ? 'bg-cyan-50 text-[#36bee3]' : 'bg-rose-50 text-rose-500'">{{ task.priority }}</span>
                  </span>
                  <span class="text-right">
                    <span class="rounded-lg px-3 py-1 text-xs" :class="task.status === 'Done' ? 'bg-emerald-50 text-emerald-500' : 'bg-amber-50 text-amber-500'">{{ task.status }}</span>
                  </span>

                  <div v-if="activeMenuTask === task.code" class="absolute left-[200px] top-10 z-20 w-52 overflow-hidden rounded-xl border border-slate-200 bg-white py-2 text-sm font-bold shadow-[0_18px_50px_rgba(31,45,74,0.12)]">
                    <button class="flex h-10 w-full items-center gap-3 px-4 text-slate-600 hover:bg-slate-50" type="button"><Settings2 class="h-4 w-4" />重命名任务</button>
                    <button class="flex h-10 w-full items-center gap-3 px-4 text-slate-600 hover:bg-slate-50" type="button"><Warehouse class="h-4 w-4" />移到库存协同</button>
                    <button class="flex h-10 w-full items-center gap-3 px-4 text-slate-600 hover:bg-slate-50" type="button"><MessageCircle class="h-4 w-4" />复制链接</button>
                    <button class="flex h-10 w-full items-center gap-3 px-4 text-rose-500 hover:bg-rose-50" type="button"><X class="h-4 w-4" />删除任务</button>
                  </div>
                </div>
              </div>
            </section>
          </div>

          <div v-if="showCalendarPicker" class="absolute left-1/2 top-[52%] z-30 w-[315px] -translate-x-1/2 rounded-2xl border border-slate-200 bg-white p-5 shadow-[0_24px_70px_rgba(31,45,74,0.16)]">
            <div class="mb-5 flex items-center justify-between">
              <button class="flex h-7 w-7 items-center justify-center rounded-full border border-slate-100" type="button"><ChevronLeft class="h-4 w-4" /></button>
              <p class="text-sm font-black">March 2026</p>
              <button class="flex h-7 w-7 items-center justify-center rounded-full border border-slate-100" type="button"><ChevronRight class="h-4 w-4" /></button>
            </div>
            <div class="grid grid-cols-7 gap-2 text-center text-xs font-bold text-slate-400">
              <span v-for="day in ['Sun','Mon','Tue','Wed','Thu','Fri','Sat']" :key="day">{{ day }}</span>
              <button v-for="date in 35" :key="date" class="h-8 rounded-full font-bold" :class="date === 19 ? 'bg-[#536dff] text-white' : 'text-slate-500 hover:bg-slate-50'" type="button" @click="showCalendarPicker = false">{{ date <= 2 ? '' : date - 2 }}</button>
            </div>
          </div>
        </div>
      </section>
    </div>

    <Transition name="product-drawer">
      <div v-if="activeSection === 'products' && isProductDrawerOpen" class="fixed inset-0 z-50 flex justify-end" aria-modal="true" role="dialog">
        <button class="absolute inset-0 bg-slate-900/20 backdrop-blur-[1px]" type="button" aria-label="关闭商品详情抽屉" @click="closeProductDrawer"></button>

        <aside data-testid="product-detail-drawer" class="product-drawer-panel relative z-10 h-full w-[50vw] min-w-[620px] max-w-[840px] max-[900px]:min-w-0 max-[900px]:w-[calc(100vw-24px)] overflow-y-auto border-l border-slate-200 bg-white p-6 shadow-[0_24px_80px_rgba(31,45,74,0.24)]">
          <div class="mb-6 flex items-start justify-between gap-4">
            <div>
              <p class="text-sm font-bold text-slate-400">商品详情</p>
              <h2 class="mt-1 text-2xl font-black leading-tight text-[#25314d]">{{ selectedProduct.name }}</h2>
            </div>
            <button data-testid="close-product-detail-drawer" class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-slate-200 text-slate-400 transition hover:bg-slate-50 hover:text-[#25314d]" type="button" aria-label="关闭详情" @click="closeProductDrawer">
              <X class="h-5 w-5" aria-hidden="true" />
            </button>
          </div>

          <div class="grid gap-5 min-[1180px]:grid-cols-[260px_minmax(0,1fr)]">
            <section class="rounded-2xl border border-slate-100 bg-slate-50 p-4">
              <div class="mb-3 flex items-center justify-between">
                <p class="text-xs font-black text-slate-400">产品图片</p>
                <span class="rounded-full bg-white px-3 py-1 text-[11px] font-black text-[#536dff]">{{ selectedProductDetails.productImage }}</span>
              </div>
              <div class="flex aspect-[4/3] items-center justify-center rounded-2xl bg-white text-[#536dff] shadow-inner shadow-slate-100">
                <PackageOpen class="h-16 w-16" aria-hidden="true" />
              </div>
            </section>

            <section class="rounded-2xl border border-slate-100 p-4">
              <div class="mb-4 flex flex-wrap items-center gap-2">
                <span class="inline-flex rounded-lg px-3 py-1 text-xs font-black" :class="productStatusClass(selectedProduct.status)">{{ selectedProduct.status }}</span>
                <span class="inline-flex rounded-lg px-3 py-1 text-xs font-black" :class="productAuditClass(selectedProduct.audit)">{{ selectedProduct.audit }}</span>
              </div>
              <div class="grid grid-cols-2 gap-3 text-sm">
                <section class="rounded-xl bg-slate-50 p-3">
                  <p class="text-xs font-bold text-slate-400">SKU</p>
                  <p class="mt-2 font-black text-[#25314d]">{{ selectedProduct.sku }}</p>
                </section>
                <section class="rounded-xl bg-slate-50 p-3">
                  <p class="text-xs font-bold text-slate-400">SPU</p>
                  <p class="mt-2 font-black text-[#25314d]">{{ selectedProduct.spu }}</p>
                </section>
                <section class="rounded-xl bg-slate-50 p-3">
                  <p class="text-xs font-bold text-slate-400">货号</p>
                  <p class="mt-2 font-black text-[#25314d]">{{ selectedProductDetails.articleNo }}</p>
                </section>
                <section class="rounded-xl bg-slate-50 p-3">
                  <p class="text-xs font-bold text-slate-400">规格</p>
                  <p class="mt-2 font-black text-[#25314d]">{{ selectedProductDetails.spec }}</p>
                </section>
                <section class="rounded-xl bg-slate-50 p-3">
                  <p class="text-xs font-bold text-slate-400">单价</p>
                  <p class="mt-2 font-black text-[#25314d]">{{ selectedProduct.price }}</p>
                </section>
                <section class="rounded-xl bg-slate-50 p-3">
                  <p class="text-xs font-bold text-slate-400">渠道商</p>
                  <p class="mt-2 font-black text-[#25314d]">{{ selectedProductDetails.distributor }}</p>
                </section>
              </div>
            </section>
          </div>

          <section class="mt-5 rounded-2xl border border-slate-100 p-4">
            <h3 class="mb-4 text-base font-black text-[#25314d]">包装尺寸</h3>
            <div class="grid gap-3 sm:grid-cols-2">
              <div class="rounded-xl bg-slate-50 p-3">
                <p class="text-xs font-bold text-slate-400">外箱尺寸（长、宽、高）</p>
                <p class="mt-2 font-black text-[#25314d]">{{ selectedProductDetails.outerBoxSize }}</p>
              </div>
              <div class="rounded-xl bg-slate-50 p-3">
                <p class="text-xs font-bold text-slate-400">内盒尺寸</p>
                <p class="mt-2 font-black text-[#25314d]">{{ selectedProductDetails.innerBoxSize }}</p>
              </div>
              <div class="rounded-xl bg-slate-50 p-3">
                <p class="text-xs font-bold text-slate-400">内盒包装</p>
                <p class="mt-2 font-black text-[#25314d]">{{ selectedProductDetails.innerPackaging }}</p>
              </div>
              <div class="rounded-xl bg-slate-50 p-3">
                <p class="text-xs font-bold text-slate-400">内盒重量</p>
                <p class="mt-2 font-black text-[#25314d]">{{ selectedProductDetails.innerBoxWeight }}</p>
              </div>
            </div>
          </section>

          <section class="mt-5 rounded-2xl border border-slate-100 p-4">
            <h3 class="mb-4 text-base font-black text-[#25314d]">重量与条码</h3>
            <div class="grid gap-3 sm:grid-cols-2 min-[1180px]:grid-cols-4">
              <div class="rounded-xl bg-slate-50 p-3">
                <p class="text-xs font-bold text-slate-400">单杯条码</p>
                <p class="mt-2 truncate font-black text-[#25314d]">{{ selectedProductDetails.cupBarcode }}</p>
              </div>
              <div class="rounded-xl bg-slate-50 p-3">
                <p class="text-xs font-bold text-slate-400">克重</p>
                <p class="mt-2 font-black text-[#25314d]">{{ selectedProductDetails.gramWeight }}</p>
              </div>
              <div class="rounded-xl bg-slate-50 p-3">
                <p class="text-xs font-bold text-slate-400">净重</p>
                <p class="mt-2 font-black text-[#25314d]">{{ selectedProductDetails.netWeight }}</p>
              </div>
              <div class="rounded-xl bg-slate-50 p-3">
                <p class="text-xs font-bold text-slate-400">毛重</p>
                <p class="mt-2 font-black text-[#25314d]">{{ selectedProductDetails.grossWeight }}</p>
              </div>
            </div>
          </section>

          <section class="mt-5 rounded-2xl border border-slate-100 p-4">
            <h3 class="mb-4 text-base font-black text-[#25314d]">图片资料</h3>
            <div class="grid gap-3 sm:grid-cols-2">
              <div class="rounded-xl bg-slate-50 p-3">
                <p class="text-xs font-black text-slate-400">外箱图片</p>
                <div class="mt-3 flex items-center gap-3 rounded-xl bg-white p-3">
                  <PackageOpen class="h-7 w-7 text-[#536dff]" aria-hidden="true" />
                  <span class="truncate text-sm font-black text-[#25314d]">{{ selectedProductDetails.outerBoxImage }}</span>
                </div>
              </div>
              <div class="rounded-xl bg-slate-50 p-3">
                <p class="text-xs font-black text-slate-400">内盒包装图</p>
                <div class="mt-3 flex items-center gap-3 rounded-xl bg-white p-3">
                  <PackageOpen class="h-7 w-7 text-[#36bee3]" aria-hidden="true" />
                  <span class="truncate text-sm font-black text-[#25314d]">{{ selectedProductDetails.innerPackagingImage }}</span>
                </div>
              </div>
            </div>
          </section>

          <section class="mt-5 rounded-2xl border border-slate-100 p-4">
            <div class="mb-5 flex items-center justify-between">
              <h3 class="text-base font-black text-[#25314d]">资料完整度</h3>
              <span class="rounded-full bg-blue-50 px-3 py-1 text-xs font-black text-[#536dff]">86%</span>
            </div>
            <div class="space-y-4">
              <section v-for="segment in productCompletionSegments" :key="segment.label">
                <div class="mb-2 flex items-center justify-between text-xs font-black">
                  <span class="text-slate-500">{{ segment.label }}</span>
                  <span class="text-slate-400">{{ segment.value }}%</span>
                </div>
                <div class="h-2 overflow-hidden rounded-full bg-slate-100">
                  <span class="block h-full rounded-full" :class="segment.color" :style="{ width: `${segment.value}%` }"></span>
                </div>
              </section>
            </div>
          </section>

          <div class="mt-6 grid grid-cols-2 gap-3">
            <button class="h-11 rounded-xl border border-slate-200 text-sm font-black text-slate-600 transition hover:bg-slate-50" type="button">同步渠道</button>
            <button class="h-11 rounded-xl bg-[#536dff] text-sm font-black text-white shadow-lg shadow-blue-200" type="button">编辑资料</button>
          </div>
        </aside>
      </div>
    </Transition>
  </main>
</template>

<style scoped>
.bebefish-prototype button:focus {
  outline: none;
}

.bebefish-prototype button:focus-visible {
  outline: none;
}

.bebefish-prototype input:focus-visible {
  outline: 2px solid rgba(83, 109, 255, 0.28);
  outline-offset: 3px;
}

.product-drawer-enter-active,
.product-drawer-leave-active {
  transition: opacity 180ms ease;
}

.product-drawer-enter-from,
.product-drawer-leave-to {
  opacity: 0;
}

.product-drawer-enter-active .product-drawer-panel,
.product-drawer-leave-active .product-drawer-panel {
  transition: transform 220ms ease;
}

.product-drawer-enter-from .product-drawer-panel,
.product-drawer-leave-to .product-drawer-panel {
  transform: translateX(28px);
}
</style>