import { request } from '../../../services/http';
import { createService } from '../../../services/serviceFactory';
import type { PageResult } from '../../masterdata/types';
import { calculateCost, validateCostLine, type CostLine, type CostResult } from './pddCostCalculator';

export interface CostPlanSummary { id: number; name: string; remark: string; version: number; archived: boolean; lineCount: number; updatedAt: string }
export interface CostPlan { id: number; name: string; remark: string; version: number; archived: boolean; formulaVersion: number; lines: {input: CostLine; result: CostResult}[]; updatedAt: string }
export interface SaveCostPlan { name: string; remark: string; version: number | null; lines: CostLine[] }
export interface CostSkuOption { skuId: number; skuCode: string; productName: string; specification: string; unitCost: number | null; salePrice: number | null }
export interface CostPlanQuery { keyword: string; archived: boolean; page: number; size: number }
export interface PddCostService {
  list(query: CostPlanQuery): Promise<PageResult<CostPlanSummary>>;
  get(id: number): Promise<CostPlan>;
  save(id: number | null, input: SaveCostPlan): Promise<CostPlan>;
  archive(id: number, version: number, archived: boolean): Promise<void>;
  skuOptions(keyword: string): Promise<CostSkuOption[]>;
}
export const httpPddCostService: PddCostService = {
  list: query => request(`/api/product-cost-plans?${new URLSearchParams(Object.fromEntries(Object.entries(query).map(([k,v])=>[k,String(v)])))}`),
  get: id => request(`/api/product-cost-plans/${id}`),
  save: (id,input) => request(`/api/product-cost-plans${id===null?'':`/${id}`}`, {method:id===null?'POST':'PUT',body:JSON.stringify(input)}),
  archive: (id,version,archived) => request(`/api/product-cost-plans/${id}/${archived?'archive':'restore'}?version=${version}`, {method:'POST'}),
  skuOptions: keyword => request(`/api/product-cost-plans/sku-options?${new URLSearchParams({keyword})}`)
};
// 模拟模式也显式持久化方案；真实接口失败不退回到本地保存。
export function createMockPddCostService(): PddCostService {
  const key='bebefish_pdd_cost_plans_v1';
  const read=(): CostPlan[] => JSON.parse(localStorage.getItem(key)??'[]');
  const write=(plans:CostPlan[])=>localStorage.setItem(key,JSON.stringify(plans));
  return {
    async list(query) {
      const all=read().filter(p=>p.archived===query.archived && p.name.toLowerCase().includes(query.keyword.trim().toLowerCase())).sort((a,b)=>b.updatedAt.localeCompare(a.updatedAt));
      return {records:all.slice((query.page-1)*query.size,query.page*query.size).map(p=>({...p,lineCount:p.lines.length})),page:query.page,pageSize:query.size,total:all.length};
    },
    async get(id) { const plan=read().find(p=>p.id===id); if(!plan) throw new Error('成本方案不存在'); return plan; },
    async save(id,input) {
      if(!input.name.trim() || input.name.trim().length>120 || input.remark.length>1000 || !input.lines.length || input.lines.length>200) throw new Error('请核对方案名称、备注及规格数量');
      for(const line of input.lines) { const error=validateCostLine(line); if(error) throw new Error(error); }
      const plans=read(); const previous=plans.find(p=>p.id===id);
      if(id!==null && (!previous || previous.archived || previous.version!==input.version)) throw new Error('方案已被修改或归档，请重新打开后操作');
      const plan:CostPlan={id:id??Math.max(0,...plans.map(p=>p.id))+1,name:input.name.trim(),remark:input.remark,version:(previous?.version??0)+1,archived:false,formulaVersion:1,
        lines:input.lines.map(line=>({input:{...line},result:calculateCost(line)!})),updatedAt:new Date().toISOString()};
      write([...plans.filter(p=>p.id!==id),plan]);return plan;
    },
    async archive(id,version,archived) {
      const plans=read(); const plan=plans.find(p=>p.id===id);
      if(!plan || plan.version!==version || plan.archived===archived) throw new Error('方案已变更，请刷新后操作');
      Object.assign(plan,{archived,version:version+1,updatedAt:new Date().toISOString()});write(plans);
    },
    async skuOptions(keyword) {
      const { mockProductService }=await import('../mockProductService');
      const result=await mockProductService.listProducts({page:1,size:50,keyword,status:'enabled'});
      return result.records.flatMap(p=>p.skus.filter(s=>s.status==='enabled').map(s=>({skuId:s.id,skuCode:s.skuCode,productName:p.productName,
        specification:s.specificationValues.join(' / ')||s.skuName,unitCost:s.standardCost,salePrice:s.defaultSalePrice})));
    }
  };
}
export const pddCostService=createService(createMockPddCostService,()=>httpPddCostService);
