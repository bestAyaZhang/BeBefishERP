import { request } from '../../services/http';
import type { ListQuery, LogisticsService } from './types';
function query(input:ListQuery = {}) {
  const params=new URLSearchParams({page:String(input.page ?? 1),size:String(input.size ?? 20)});
  if(input.keyword?.trim()) params.set('keyword',input.keyword.trim());
  if(input.status) params.set('status',input.status);
  return `?${params}`;
}
const body=(method:string,input:unknown):RequestInit=>({method,body:JSON.stringify(input)});
export const httpLogisticsService:LogisticsService = {
  listCompanies:input=>request(`/api/logistics/companies${query(input)}`),
  getCompany:id=>request(`/api/logistics/companies/${id}`),
  createCompany:input=>request('/api/logistics/companies',body('POST',input)),
  updateCompany:(id,input)=>request(`/api/logistics/companies/${id}`,body('PUT',input)),
  changeCompanyStatus:(id,status,version)=>request(`/api/logistics/companies/${id}/status`,body('POST',{status,version})),
  getAccount:id=>request(`/api/logistics/companies/${id}/account`),
  saveAccount:(id,input)=>request(`/api/logistics/companies/${id}/account`,body('PUT',input)),
  listAccounts:(id,input)=>request(`/api/logistics/companies/${id}/accounts${query(input)}`),
  listTemplates:(id,input)=>request(`/api/logistics/companies/${id}/templates${query(input)}`),
  createTemplate:(id,input)=>request(`/api/logistics/companies/${id}/templates`,body('POST',input)),
  getTemplate:id=>request(`/api/logistics/templates/${id}`),
  updateTemplate:(id,input)=>request(`/api/logistics/templates/${id}`,body('PUT',input)),
  changeTemplateStatus:(id,status,version)=>request(`/api/logistics/templates/${id}/status`,body('POST',{status,version})),
  getProvinces:()=>request('/api/logistics/provinces'),
  getQuotes:input=>request('/api/logistics/quotes',body('POST',input)),
  getFreightSelection:id=>request(`/api/shipments/${id}/freight-selection`),
  confirmLogisticsAccount:(id,input)=>request(`/api/shipments/${id}/logistics-account`,body('POST',input))
};
