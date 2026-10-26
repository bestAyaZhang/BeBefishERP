import { describe, expect, it } from 'vitest';
import { createMockLogisticsService } from './mockLogisticsService';
import type { CompanyInput } from './types';
const company:CompanyInput={code:'TEST',name:'本地测试物流',provider:'MANUAL',status:'enabled',contactName:'',contactPhone:'',sortOrder:0,remark:'',version:0};
describe('controlled local logistics data',()=>{
  it('starts without invented rates and rejects stale company updates',async()=>{
    const service=createMockLogisticsService();
    expect((await service.listCompanies()).total).toBe(0);
    const saved=await service.createCompany(company);
    const changed=await service.updateCompany(saved.id,{...company,name:'新名称',version:saved.version});
    expect(changed.name).toBe('新名称');
    await expect(service.changeCompanyStatus(saved.id,'disabled',saved.version)).rejects.toThrow('已更新');
  });
  it('never returns secrets and does not transfer them to a changed identity',async()=>{
    const service=createMockLogisticsService(),saved=await service.createCompany(company);
    const input={version:0,code:'a',customerCode:'c',dictId:'d',orderUrl:'',appKey:'top-secret',customerPass:'password',sender:{name:'',phone:'',province:'',city:'',county:'',address:''}};
    const account=await service.saveAccount(saved.id,input);
    expect(JSON.stringify(account)).not.toContain('top-secret');
    const preserved=await service.saveAccount(saved.id,{...input,appKey:'',customerPass:'',version:account.version});
    expect(preserved.appKeyConfigured).toBe(true);
    const replaced=await service.saveAccount(saved.id,{...input,code:'other',appKey:'',customerPass:'',version:preserved.version});
    expect(replaced.id).not.toBe(account.id);expect(replaced.appKeyConfigured).toBe(false);
    expect(replaced.ready).toBe(false);
    expect(JSON.stringify(await service.listAccounts(saved.id))).not.toContain('top-secret');
  });
  it('provides all province codes and refuses historical real-order linkage in mock',async()=>{
    const service=createMockLogisticsService();
    const provinces=await service.getProvinces();
    expect(provinces).toHaveLength(34);expect(new Set(provinces.map(p=>p.code)).size).toBe(34);
    await expect(service.confirmLogisticsAccount(1,{accountId:1,version:1,confirmed:true})).rejects.toThrow('真实');
  });
  it('increments the parent company version when account configuration is saved',async()=>{
    const service=createMockLogisticsService(),saved=await service.createCompany(company);
    await service.saveAccount(saved.id,{version:0,code:'a',customerCode:'c',dictId:'d',orderUrl:'',sender:{name:'',phone:'',province:'',city:'',county:'',address:''}});
    expect((await service.getCompany(saved.id)).version).toBe(saved.version+1);
    await expect(service.updateCompany(saved.id,{...company,version:saved.version})).rejects.toThrow('已更新');
  });
});
