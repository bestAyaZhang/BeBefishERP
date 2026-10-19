import { beforeEach,afterEach,describe,it,expect,vi } from 'vitest';
import { ACCESS_TOKEN_STORAGE_KEY } from '../../../types/auth';
import { httpPddCostService,createMockPddCostService } from './pddCostService';
import { emptyCostLine } from './pddCostCalculator';
describe('PDD cost persistence',()=>{
  beforeEach(()=>{localStorage.clear();localStorage.setItem(ACCESS_TOKEN_STORAGE_KEY,'test');vi.stubGlobal('fetch',vi.fn().mockImplementation(async()=>new Response(JSON.stringify({code:'SUCCESS',data:{id:7}}),{status:200,headers:{'Content-Type':'application/json'}})));});
  afterEach(()=>{vi.unstubAllGlobals();localStorage.clear();});
  it('uses real create/update/read/archive APIs and sends version checks',async()=>{
    const input={name:'成本',remark:'',version:2,lines:[{...emptyCostLine(),specification:'透明',unitCost:2,salePrice:5}]};
    await httpPddCostService.save(null,input);await httpPddCostService.save(7,input);await httpPddCostService.get(7);
    await httpPddCostService.archive(7,3,true);await httpPddCostService.archive(7,4,false);
    expect(fetch).toHaveBeenNthCalledWith(1,'/api/product-cost-plans',expect.objectContaining({method:'POST',body:JSON.stringify(input)}));
    expect(fetch).toHaveBeenNthCalledWith(2,'/api/product-cost-plans/7',expect.objectContaining({method:'PUT'}));
    expect(fetch).toHaveBeenNthCalledWith(4,'/api/product-cost-plans/7/archive?version=3',expect.objectContaining({method:'POST'}));
    expect(fetch).toHaveBeenNthCalledWith(5,'/api/product-cost-plans/7/restore?version=4',expect.objectContaining({method:'POST'}));
  });
  it('does not save locally when real persistence fails',async()=>{
    vi.mocked(fetch).mockRejectedValue(new Error('offline'));
    await expect(httpPddCostService.save(null,{name:'成本',remark:'',version:null,lines:[]})).rejects.toThrow();
    expect(localStorage.getItem('bebefish_pdd_cost_plans_v1')).toBeNull();
  });
  it('persists mock plans across service instances and detects stale updates',async()=>{
    const first=createMockPddCostService();const input={name:'成本',remark:'',version:null,lines:[{...emptyCostLine(),specification:'透明',unitCost:2,salePrice:5}]};
    const plan=await first.save(null,input);const second=createMockPddCostService();
    expect((await second.get(plan.id)).lines[0].result.netProfit).toBe(3);
    await first.save(plan.id,{...input,version:1,name:'已更新'});
    await expect(second.save(plan.id,{...input,version:1})).rejects.toThrow('已被修改');
  });
});
