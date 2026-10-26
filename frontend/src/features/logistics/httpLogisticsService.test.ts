import { afterEach, describe, expect, it, vi } from 'vitest';
import { httpLogisticsService as service } from './httpLogisticsService';
import type {QuoteInput} from './types';
afterEach(()=>vi.unstubAllGlobals());
const response=(data:unknown)=>new Response(JSON.stringify({code:'SUCCESS',message:'成功',data}),{status:200});
describe('logistics HTTP boundary',()=>{
  it('unwraps paged account metadata instead of treating it as a raw array',async()=>{
    const data={records:[],page:2,pageSize:10,total:11};const fetch=vi.fn().mockResolvedValue(response(data));vi.stubGlobal('fetch',fetch);
    expect(await service.listAccounts(3,{page:2,size:10})).toEqual(data);
    expect(fetch.mock.calls[0][0]).toBe('/api/logistics/companies/3/accounts?page=2&size=10');
  });
  it('posts only the quote contract and propagates backend validation errors',async()=>{
    const fetch=vi.fn().mockResolvedValue(new Response(JSON.stringify({code:'VALIDATION_ERROR',message:'无法识别收件省份'}),{status:400}));vi.stubGlobal('fetch',fetch);
    const input:QuoteInput={senderProvince:'浙江省',senderCity:'金华市',senderCounty:'义乌市',recipientProvince:'未知',recipientCity:'',recipientCounty:'',weight:10,volume:null,pieceAmount:1,goodsType:1,payType:1};
    await expect(service.getQuotes(input)).rejects.toThrow('无法识别收件省份');
    expect(fetch.mock.calls[0][0]).toBe('/api/logistics/quotes');
    expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual(input);expect(fetch.mock.calls[0][1].method).toBe('POST');
  });
});
