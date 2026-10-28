import { describe, expect, it } from 'vitest';
import { createMockLogisticsService } from './mockLogisticsService';
import type { CompanyInput } from './types';
import { newTemplate } from './templateRules';
const company:CompanyInput={code:'TEST',name:'本地测试物流',provider:'MANUAL',status:'enabled',contactName:'',contactPhone:'',sortOrder:0,remark:'',version:0};
describe('controlled local logistics data',()=>{
  it('validates Yimi service products during both template creation and updates',async()=>{
    const service=createMockLogisticsService(),saved=await service.createCompany({...company,provider:'YIMI'});
    const template={...newTemplate(),name:'壹米合同',productCode:'1',productName:'港到门'};
    await expect(service.createTemplate(saved.id,{...template,productCode:'524'})).rejects.toThrow('港到门');
    const created=await service.createTemplate(saved.id,template);
    await expect(service.updateTemplate(created.id,{...created,productCode:'524'})).rejects.toThrow('港到门');
  });
  it('retains only Yimi configuration flags and creates a new account when seller or app identity changes',async()=>{
    const service=createMockLogisticsService(),saved=await service.createCompany({...company,provider:'YIMI'});
    const input={version:0,code:'',customerCode:'',dictId:'',orderUrl:'',appKey:'test-app-key',appSecret:'test-app-secret',yimi:{sellerId:'seller',subscriptionId:'subscription'},sender:{name:'',phone:'',province:'',city:'',county:'',address:''}};
    const initial=await service.saveAccount(saved.id,input);
    expect(initial.appSecretConfigured).toBe(true);expect(initial.yimi).toEqual({...input.yimi,designatedPickup:false,salesmanCode:'',salesmanName:''});
    expect(JSON.stringify(await service.listAccounts(saved.id))).not.toContain('test-app');
    const preserved=await service.saveAccount(saved.id,{...input,version:initial.version,appKey:'',appSecret:''});
    expect(preserved.id).toBe(initial.id);expect(preserved.appSecretConfigured).toBe(true);
    const changed=await service.saveAccount(saved.id,{...input,version:preserved.version,appKey:'',appSecret:'',yimi:{...input.yimi,sellerId:'other'}});
    expect(changed.id).not.toBe(initial.id);expect(changed.appSecretConfigured).toBe(false);expect(changed.appKeyConfigured).toBe(false);
    const newApp=await service.saveAccount(saved.id,{...input,version:changed.version,appKey:'new-app-key',appSecret:'',yimi:changed.yimi});
    expect(newApp.id).not.toBe(changed.id);expect(newApp.appSecretConfigured).toBe(false);
    expect(newApp.ready).toBe(false);
  });
  it('keeps designated pickup in public metadata without changing identity or retaining secrets',async()=>{
    const service=createMockLogisticsService(),saved=await service.createCompany({...company,provider:'YIMI'});
    const input={version:0,code:'',customerCode:'',dictId:'',orderUrl:'',appKey:'private-app-key',appSecret:'private-app-secret',yimi:{sellerId:'seller',subscriptionId:'subscription',appSecret:'nested-private-secret'},sender:{name:'',phone:'',province:'',city:'',county:'',address:''}};
    const initial=await service.saveAccount(saved.id,input);
    expect(initial.yimi).toEqual({sellerId:'seller',subscriptionId:'subscription',designatedPickup:false,salesmanCode:'',salesmanName:''});
    const changed=await service.saveAccount(saved.id,{...input,version:initial.version,appKey:'',appSecret:'',yimi:{sellerId:'seller',subscriptionId:'subscription',designatedPickup:true,salesmanCode:'00123',salesmanName:'揽收员甲'}});
    expect(changed.id).toBe(initial.id);expect(changed.accountVersionId).not.toBe(initial.accountVersionId);
    expect(changed.appKeyConfigured).toBe(true);expect(changed.appSecretConfigured).toBe(true);
    expect(changed.yimi).toEqual({sellerId:'seller',subscriptionId:'subscription',designatedPickup:true,salesmanCode:'00123',salesmanName:'揽收员甲'});
    expect((await service.getAccount(saved.id))?.yimi).toEqual(changed.yimi);
    expect((await service.listAccounts(saved.id)).records).toHaveLength(1);
    const publicData=JSON.stringify(await service.listAccounts(saved.id));
    expect(publicData).not.toContain('private-app');expect(publicData).not.toContain('nested-private');
    expect(changed.ready).toBe(false);
  });
  it.each([
    {salesmanCode:'00123',salesmanName:''},
    {salesmanCode:'',salesmanName:'揽收员甲'},
    {designatedPickup:false,salesmanCode:'00123',salesmanName:'揽收员甲'}
  ])('normalizes partially configured pickup without claiming API readiness: %o',async pickup=>{
    const service=createMockLogisticsService(),saved=await service.createCompany({...company,provider:'YIMI'});
    const result=await service.saveAccount(saved.id,{version:0,code:'',customerCode:'',dictId:'',orderUrl:'',yimi:{sellerId:'seller',subscriptionId:'subscription',...pickup},sender:{name:'',phone:'',province:'',city:'',county:'',address:''}});
    expect(result.yimi).toEqual({sellerId:'seller',subscriptionId:'subscription',...pickup,designatedPickup:true});
    expect(result.ready).toBe(false);
  });
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
