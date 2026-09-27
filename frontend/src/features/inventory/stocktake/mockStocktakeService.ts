import type {
  CreateStocktakeTaskInput,
  StocktakeCountInput,
  StocktakeItem,
  StocktakeService,
  StocktakeStatus,
  StocktakeTask,
  StocktakeTaskDetails
} from './types';

const now = '2026-09-17T09:20:00';

const seedTasks: StocktakeTaskDetails[] = [
  {
    id: 1, taskNo: 'PD20260917001', warehouseId: 1, warehouseName: '杭州主仓',
    scopeLabel: '全仓 · 8区23堆', assigneeName: '张敏', countedItems: 4, totalItems: 6,
    differenceItems: 0, status: 'in_progress', createdAt: now, blindCount: true,
    items: [
      item(101, 'A-01', 'ST-001', 'SKU-FISH-500ML-红', '高脚玻璃杯', '红色款', '红色 / 500ml', 24, 288, null),
      item(102, 'A-01', 'ST-002', 'SKU-CUP-12OZ', '随行杯', '透明款', '透明 / 12oz', 12, 120, 120),
      item(103, 'B-01', 'ST-006', 'SKU-TEA-1L-绿', '茶壶', '绿色款', '绿色 / 1L', 20, 80, 80),
      item(104, 'B-01', 'ST-008', 'SKU-FISH-350ML-橙', '玻璃杯', '橙色款', '橙色 / 350ml', 24, 576, 568),
      item(105, 'C-01', 'ST-011', 'SKU-BOWL-8IN-白', '餐碗', '白色款', '白色 / 8in', 18, 216, null),
      item(106, 'C-01', 'ST-014', 'SKU-PLATE-10IN', '餐盘', '透明款', '透明 / 10in', 12, 144, 142)
    ]
  },
  summaryTask(2, 'PD20260916003', 2, '义乌备货仓', 'A区 · 6个货物堆', '李娜', 42, 42, 6, 'awaiting_recount', '2026-09-16T14:35:00'),
  summaryTask(3, 'PD20260916002', 3, '上海中转仓', '重点 SKU · 18项', '王强', 18, 18, 3, 'awaiting_approval', '2026-09-16T10:10:00'),
  summaryTask(4, 'PD20260915008', 1, '杭州主仓', 'B区 · 10个货物堆', '陈杰', 56, 56, 0, 'completed', '2026-09-15T16:40:00'),
  summaryTask(5, 'PD20260914005', 4, '宁波成品仓', '全仓 · 5区16堆', '周颖', 0, 72, 0, 'not_started', '2026-09-14T11:20:00'),
  summaryTask(6, 'PD20260913007', 2, '义乌备货仓', 'C区 · 4个货物堆', '李娜', 31, 31, 0, 'completed', '2026-09-13T08:50:00')
];

let tasks = seedTasks.map(cloneTask);

function item(
  id: number,
  zoneName: string,
  palletId: string,
  skuCode: string,
  productName: string,
  skuName: string,
  specification: string,
  unitsPerCase: number,
  bookQuantity: number,
  firstCountQuantity: number | null
): StocktakeItem {
  return {
    id, zoneName, palletId, palletLabel: palletId, skuId: 400 + id, skuCode, productName, skuName,
    specification, unitsPerCase, bookQuantity, firstCountQuantity, recountQuantity: null,
    difference: firstCountQuantity === null ? null : firstCountQuantity - bookQuantity,
    status: firstCountQuantity === null ? 'uncounted' : 'counted'
  };
}

function summaryTask(
  id: number,
  taskNo: string,
  warehouseId: number,
  warehouseName: string,
  scopeLabel: string,
  assigneeName: string,
  countedItems: number,
  totalItems: number,
  differenceItems: number,
  status: StocktakeStatus,
  createdAt: string
): StocktakeTaskDetails {
  return {
    id, taskNo, warehouseId, warehouseName, scopeLabel, assigneeName, countedItems, totalItems,
    differenceItems, status, createdAt, blindCount: true, items: []
  };
}

function cloneTask(task: StocktakeTaskDetails): StocktakeTaskDetails {
  return { ...task, items: task.items.map((entry) => ({ ...entry })) };
}

function updateCounts(task: StocktakeTaskDetails, counts: StocktakeCountInput[]) {
  const byItem = new Map(counts.map((count) => [count.itemId, count.quantity]));
  task.items.forEach((entry) => {
    if (!byItem.has(entry.id)) return;
    entry.firstCountQuantity = byItem.get(entry.id) ?? null;
    entry.difference = entry.firstCountQuantity === null ? null : entry.firstCountQuantity - (entry.bookQuantity ?? 0);
    entry.status = entry.firstCountQuantity === null ? 'uncounted' : 'counted';
  });
  task.countedItems = task.items.filter((entry) => entry.firstCountQuantity !== null).length;
}

export const mockStocktakeService: StocktakeService = {
  async listTasks(query) {
    const keyword = query.keyword?.trim().toLowerCase();
    const filtered = tasks.filter((task) => {
      if (query.warehouseId !== undefined && task.warehouseId !== query.warehouseId) return false;
      if (query.status && task.status !== query.status) return false;
      return !keyword || [task.taskNo, task.warehouseName, task.assigneeName]
        .some((value) => value.toLowerCase().includes(keyword));
    });
    const currentMonth = '2026-09';
    return {
      summary: {
        inProgress: tasks.filter((task) => task.status === 'in_progress').length,
        awaitingRecount: tasks.filter((task) => task.status === 'awaiting_recount').length,
        awaitingApproval: tasks.filter((task) => task.status === 'awaiting_approval').length,
        completedThisMonth: tasks.filter((task) => task.status === 'completed' && task.createdAt.startsWith(currentMonth)).length,
        discrepancyItems: tasks.reduce((total, task) => total + task.differenceItems, 0),
        accuracyRate: 98.6
      },
      tasks: filtered.map(({ items: _items, blindCount: _blindCount, ...task }) => ({ ...task }))
    };
  },
  async getTask(id) {
    const task = tasks.find((entry) => entry.id === id);
    if (!task) throw new Error('盘点任务不存在');
    return cloneTask(task);
  },
  async createTask(input: CreateStocktakeTaskInput) {
    const id = Math.max(0, ...tasks.map((task) => task.id)) + 1;
    const task = summaryTask(
      id, `PD20260917${String(id).padStart(3, '0')}`, input.warehouseId,
      `仓库 ${input.warehouseId}`,
      input.palletIds?.length ? `指定货物堆 · ${input.palletIds.length}堆` : '全仓',
      '当前登录用户', 0, 0, 0, 'not_started', now
    );
    task.blindCount = input.blindCount;
    tasks = [task, ...tasks];
    return cloneTask(task);
  },
  async saveDraft(id, counts) {
    const task = tasks.find((entry) => entry.id === id);
    if (!task) throw new Error('盘点任务不存在');
    updateCounts(task, counts);
    task.status = task.countedItems > 0 ? 'in_progress' : 'not_started';
    return cloneTask(task);
  },
  async submitInitial(id, counts) {
    const task = tasks.find((entry) => entry.id === id);
    if (!task) throw new Error('盘点任务不存在');
    updateCounts(task, counts);
    if (task.items.some((entry) => entry.firstCountQuantity === null)) throw new Error('请完成全部盘点明细');
    task.differenceItems = task.items.filter((entry) => entry.difference !== 0).length;
    task.status = task.differenceItems > 0 ? 'awaiting_recount' : 'awaiting_approval';
    task.items.forEach((entry) => {
      entry.status = entry.difference === 0 ? 'matched' : 'difference';
    });
    return cloneTask(task);
  },
  async submitRecount(id, counts) {
    const task = tasks.find((entry) => entry.id === id);
    if (!task || task.status !== 'awaiting_recount') throw new Error('当前任务不可复盘');
    const byItem = new Map(counts.map((count) => [count.itemId, count.quantity]));
    const differences = task.items.filter((entry) => entry.difference !== 0);
    if (differences.some((entry) => !byItem.has(entry.id))) throw new Error('请完成全部差异项复盘');
    differences.forEach((entry) => {
      entry.recountQuantity = byItem.get(entry.id) ?? null;
      entry.difference = entry.recountQuantity === null ? null : entry.recountQuantity - (entry.bookQuantity ?? 0);
      entry.status = entry.difference === 0 ? 'matched' : 'recount_required';
    });
    task.differenceItems = differences.filter((entry) => entry.difference !== 0).length;
    task.status = 'awaiting_approval';
    return cloneTask(task);
  },
  async approve(id) {
    const task = tasks.find((entry) => entry.id === id);
    if (!task || task.status !== 'awaiting_approval') throw new Error('当前任务不可审批');
    task.status = 'completed';
    task.items.forEach((entry) => { entry.status = 'approved'; });
    return cloneTask(task);
  }
};
