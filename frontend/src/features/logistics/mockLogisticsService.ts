import { provinceFixture } from './provinceFixture';
import { validateTemplate } from './templateRules';
import type { AccountView, Company, CompanyInput, ListQuery, LogisticsService, Page, Quote, QuoteInput, Template } from './types';
const clone=<T>(value:T):T=>JSON.parse(JSON.stringify(value));
const now=()=>new Date().toISOString();
function page<T extends {name:string;status:string}>(rows:T[],query:ListQuery={}):Page<T>{
  const filtered=rows.filter(row=>(!query.keyword||row.name.includes(query.keyword))&&(!query.status||row.status===query.status));
  const p=query.page??1,size=query.size??20;return clone({records:filtered.slice((p-1)*size,p*size),page:p,pageSize:size,total:filtered.length});
}
function version(actual:number,sent:number){if(actual!==sent)throw new Error('记录已更新，请刷新后重试');}
export function createMockLogisticsService():LogisticsService {
  const companies:Company[]=[],accounts:AccountView[]=[],templates:Template[]=[];
  let nextId=1;
  const company=(id:number)=>{const row=companies.find(row=>row.id===id);if(!row)throw new Error('物流公司不存在');return row;};
  const template=(id:number)=>{const row=templates.find(row=>row.id===id);if(!row)throw new Error('模板不存在');return row;};
  function checkCompany(input:CompanyInput,id?:number){if(!input.code.trim()||!input.name.trim())throw new Error('请填写公司名称和编码');if(companies.some(row=>row.id!==id && row.code===input.code))throw new Error('公司编码已存在');}
  return {
    async listCompanies(query){return page([...companies].sort((a,b)=>a.sortOrder-b.sortOrder||a.id-b.id),query);},
    async getCompany(id){return clone(company(id));},
    async createCompany(input){checkCompany(input);const row={...clone(input),id:nextId++,version:1,createdAt:now(),updatedAt:now()};companies.push(row);return clone(row);},
    async updateCompany(id,input){const row=company(id);version(row.version,input.version);checkCompany(input,id);Object.assign(row,clone(input),{version:row.version+1,updatedAt:now()});return clone(row);},
    async changeCompanyStatus(id,status,v){const row=company(id);version(row.version,v);row.status=status;row.version++;row.updatedAt=now();return clone(row);},
    async getAccount(id){company(id);return clone(accounts.filter(row=>row.companyId===id).at(-1)??null);},
    async saveAccount(id,input){
      company(id);const previous=accounts.filter(row=>row.companyId===id).at(-1);version(previous?.version??0,input.version);
      const same=previous?.code===input.code && previous?.customerCode===input.customerCode && previous?.dictId===input.dictId;
      const row:AccountView={id:same?previous!.id:nextId++,companyId:id,environment:'test',version:(previous?.version??0)+1,accountVersionId:nextId++,code:input.code,customerCode:input.customerCode,dictId:input.dictId,orderUrl:input.orderUrl,sender:clone(input.sender),appKeyConfigured:Boolean(input.appKey?.trim()||(same&&previous?.appKeyConfigured)),customerPassConfigured:Boolean(input.customerPass?.trim()||(same&&previous?.customerPassConfigured)),ready:false,message:'本地测试数据，不支持真实接口下单'};
      if(same)accounts.splice(accounts.indexOf(previous!),1);accounts.push(row);return clone(row);
    },
    async listAccounts(id,query){company(id);const rows=accounts.filter(row=>row.companyId===id),p=query?.page??1,size=query?.size??20;return clone({records:rows.slice((p-1)*size,p*size),page:p,pageSize:size,total:rows.length});},
    async listTemplates(id,query){company(id);return page(templates.filter(row=>row.companyId===id),query);},
    async createTemplate(id,input){company(id);const error=validateTemplate(input);if(error)throw new Error(error);const row:Template={...clone(input),id:nextId++,companyId:id,accountId:accounts.filter(row=>row.companyId===id).at(-1)?.id??null,version:1,updatedAt:now()};templates.push(row);return clone(row);},
    async getTemplate(id){return clone(template(id));},
    async updateTemplate(id,input){const row=template(id);version(row.version,input.version);const error=validateTemplate(input);if(error)throw new Error(error);Object.assign(row,clone(input),{version:row.version+1,updatedAt:now()});return clone(row);},
    async changeTemplateStatus(id,status,v){const row=template(id);version(row.version,v);const error=validateTemplate({...row,status});if(error)throw new Error(error);row.status=status;row.version++;return clone(row);},
    async getProvinces(){return clone(provinceFixture);},
    async getQuotes(input){return {quotes:companies.filter(row=>row.status==='enabled').flatMap(row=>{
      const active=templates.filter(t=>t.companyId===row.id && t.status==='enabled');
      return active.length?active.map(t=>mockQuote(row,t,input)):[mockQuote(row,null,input)];
    }),legacyOrderingAllowed:!templates.some(t=>t.status==='enabled'),generatedAt:now()};},
    async getFreightSelection(){return null;},
    async confirmLogisticsAccount(){throw new Error('历史真实物流账号关联必须使用真实服务，Mock 不支持');}
  };
}
function mockQuote(company:Company,template:Template|null,input:QuoteInput):Quote {
  // The local preview deliberately has no price engine or saved quote IDs.
  // Only the backend may mint a quote usable for shipment persistence.
  const province=provinceFixture.find(p=>p.code===input.recipientProvince||p.name===input.recipientProvince);
  return {quoteId:null,companyId:company.id,companyName:`${company.name}（本地测试）`,provider:company.provider,accountId:template?.accountId??null,accountVersionId:null,templateId:template?.id??null,templateVersion:template?.version??null,productCode:template?.productCode??'',productName:template?.productName??'',amount:null,billedQuantity:null,chargeMode:template?.rules.mode??'',matchedRegion:'',lowerExclusive:null,upperInclusive:null,firstQuantity:null,firstFee:null,additionalQuantity:null,additionalFee:null,renewalCount:null,available:false,reason:!province?'收件省份无法识别':!template?'未配置启用费率':'本地测试：请连接后端测算真实合同报价',apiReady:false,apiMessage:'需线下下单，本地测试不发送物流请求',recommended:false,expiresAt:null};
}
