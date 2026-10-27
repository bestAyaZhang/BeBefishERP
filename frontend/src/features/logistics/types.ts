export type CarrierProvider = 'ANE'|'MANUAL'|'YIMI';
export interface Company { id:number; code:string; name:string; provider:CarrierProvider; status:string; contactName:string; contactPhone:string; sortOrder:number; remark:string; version:number; createdAt:string; updatedAt:string }
export type CompanyInput = Omit<Company, 'id'|'createdAt'|'updatedAt'>;
export interface Sender { name:string; phone:string; province:string; city:string; county:string; address:string }
export interface YimiAccount { sellerId:string; subscriptionId:string }
export interface AccountInput { version:number; code:string; customerCode:string; dictId:string; appKey?:string; customerPass?:string; appSecret?:string; yimi?:YimiAccount; orderUrl:string; sender:Sender }
export interface AccountView { id:number; companyId:number; environment:string; version:number; accountVersionId:number; code:string; customerCode:string; dictId:string; orderUrl:string; sender:Sender; appKeyConfigured:boolean; customerPassConfigured:boolean; appSecretConfigured?:boolean; yimi?:YimiAccount; ready:boolean; message:string }
export interface Band { lowerExclusive:number; upperInclusive:number|null; firstQuantity:number|null; firstFee:number|null; additionalQuantity:number|null; additionalFee:number|null }
export interface RegionGroup { name:string; provinces:string[]; bands:Band[] }
export interface Rules { mode:'weight'|'piece'; volumetric:boolean; volumeDivisor:number; defaultBands:Band[]; regionGroups:RegionGroup[] }
export interface TemplateInput { name:string; productCode:string; productName:string; originProvince:string; originCity:string; originCounty:string; rules:Rules; status:string; remark:string; version:number }
export interface Template extends TemplateInput { id:number; companyId:number; accountId:number|null; updatedAt:string }
export interface Province { code:string; name:string; group:string }
export interface QuoteInput { senderProvince:string; senderCity:string; senderCounty:string; recipientProvince:string; recipientCity:string; recipientCounty:string; weight:number|null; volume:number|null; pieceAmount:number|null; goodsType:number|null; payType:number|null; yimi?:import('../shipping/types').YimiOrderOptions|null }
export interface Quote { quoteId:string|null; companyId:number; companyName:string; provider:string; accountId:number|null; accountVersionId:number|null; templateId:number|null; templateVersion:number|null; productCode:string; productName:string; amount:number|null; billedQuantity:number|null; chargeMode:string; matchedRegion:string; lowerExclusive:number|null; upperInclusive:number|null; firstQuantity:number|null; firstFee:number|null; additionalQuantity:number|null; additionalFee:number|null; renewalCount:number|null; available:boolean; reason:string; apiReady:boolean; apiMessage:string; recommended:boolean; expiresAt:string|null }
export interface QuoteBatch { quotes:Quote[]; legacyOrderingAllowed:boolean; generatedAt:string }
export interface LogisticsChoice { quoteId:string|null; preferredCompanyId:number|null; clear:boolean }
export interface Page<T> { records:T[]; page:number; pageSize:number; total:number }
export interface ListQuery { page?:number; size?:number; keyword?:string; status?:string }
export interface LogisticsService {
  listCompanies(query?:ListQuery):Promise<Page<Company>>;
  getCompany(id:number):Promise<Company>;
  createCompany(input:CompanyInput):Promise<Company>;
  updateCompany(id:number,input:CompanyInput):Promise<Company>;
  changeCompanyStatus(id:number,status:string,version:number):Promise<Company>;
  getAccount(id:number):Promise<AccountView|null>;
  saveAccount(id:number,input:AccountInput):Promise<AccountView>;
  listAccounts(id:number,query?:ListQuery):Promise<Page<AccountView>>;
  listTemplates(id:number,query?:ListQuery):Promise<Page<Template>>;
  createTemplate(id:number,input:TemplateInput):Promise<Template>;
  getTemplate(id:number):Promise<Template>;
  updateTemplate(id:number,input:TemplateInput):Promise<Template>;
  changeTemplateStatus(id:number,status:string,version:number):Promise<Template>;
  getProvinces():Promise<Province[]>;
  getQuotes(input:QuoteInput):Promise<QuoteBatch>;
  getFreightSelection(id:number):Promise<Quote|null>;
  confirmLogisticsAccount(id:number,input:{accountId:number;version:number;confirmed:true}):Promise<unknown>;
}
