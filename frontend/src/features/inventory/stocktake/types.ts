export type StocktakeStatus =
  | 'not_started'
  | 'in_progress'
  | 'awaiting_recount'
  | 'awaiting_approval'
  | 'completed';

export type StocktakeItemStatus =
  | 'uncounted'
  | 'counted'
  | 'matched'
  | 'difference'
  | 'recount_required'
  | 'approved';

export interface StocktakeSummary {
  inProgress: number;
  awaitingRecount: number;
  awaitingApproval: number;
  completedThisMonth: number;
  discrepancyItems: number;
  accuracyRate: number;
}

export interface StocktakeTask {
  id: number;
  taskNo: string;
  warehouseId: number;
  warehouseName: string;
  scopeLabel: string;
  assigneeName: string;
  countedItems: number;
  totalItems: number;
  differenceItems: number;
  status: StocktakeStatus;
  createdAt: string;
}

export interface StocktakeItem {
  id: number;
  zoneName: string | null;
  palletId: string;
  palletLabel: string;
  skuId: number;
  skuCode: string;
  productName: string;
  skuName: string;
  specification: string | null;
  unitsPerCase: number | null;
  bookQuantity: number;
  firstCountQuantity: number | null;
  recountQuantity: number | null;
  difference: number | null;
  status: StocktakeItemStatus;
}

export interface StocktakeTaskDetails extends StocktakeTask {
  blindCount: boolean;
  items: StocktakeItem[];
}

export interface StocktakeTaskListResult {
  summary: StocktakeSummary;
  tasks: StocktakeTask[];
}

export interface StocktakeTaskQuery {
  warehouseId?: number;
  status?: StocktakeStatus;
  keyword?: string;
}

export interface StocktakeCountInput {
  itemId: number;
  quantity: number;
}

export interface CreateStocktakeTaskInput {
  warehouseId: number;
  blindCount: boolean;
  palletIds?: string[];
}

export interface StocktakeService {
  listTasks(query: StocktakeTaskQuery): Promise<StocktakeTaskListResult>;
  getTask(id: number): Promise<StocktakeTaskDetails>;
  createTask(input: CreateStocktakeTaskInput): Promise<StocktakeTaskDetails>;
  saveDraft(id: number, counts: StocktakeCountInput[]): Promise<StocktakeTaskDetails>;
  submitInitial(id: number, counts: StocktakeCountInput[]): Promise<StocktakeTaskDetails>;
  submitRecount(id: number, counts: StocktakeCountInput[]): Promise<StocktakeTaskDetails>;
  approve(id: number): Promise<StocktakeTaskDetails>;
}
