import type { Band, RegionGroup, TemplateInput } from './types';
export function newBand():Band { return {lowerExclusive:0,upperInclusive:null,firstQuantity:null,firstFee:null,additionalQuantity:null,additionalFee:null}; }
export function newTemplate():TemplateInput {return {name:'',productCode:'',productName:'',originProvince:'',originCity:'',originCounty:'',rules:{mode:'weight',volumetric:false,volumeDivisor:6000,defaultBands:[newBand()],regionGroups:[]},status:'disabled',remark:'',version:0};}
export function moveProvinces(groups:RegionGroup[],index:number,codes:string[]):RegionGroup[]{return groups.map((group,i)=>({...group,provinces:i===index?[...codes]:group.provinces.filter(code=>!codes.includes(code))}));}
export function validateTemplate(input:TemplateInput):string{
  if(!input.name.trim())return '请填写模板名称';
  const enabled=input.status==='enabled', seen=new Set<string>();
  for(const group of input.rules.regionGroups){
    if(!group.name.trim() || !group.provinces.length)return '请填写地区组名称并选择省份';
    for(const code of group.provinces){if(seen.has(code))return '省份重复分配，请先确认移动';seen.add(code);}
  }
  if(input.rules.mode==='weight' && input.rules.volumetric && !(input.rules.volumeDivisor>0))return '体积系数必须大于零';
  if(enabled && (!input.productCode.trim() || !input.productName.trim() || !input.originProvince.trim() || !input.originCity.trim() || !input.originCounty.trim()))return '启用前请补全运输产品和发货省市区';
  if(enabled && !input.rules.defaultBands.length && !input.rules.regionGroups.length)return '启用前至少配置一组费率';
  for(const bands of [input.rules.defaultBands,...input.rules.regionGroups.map(group=>group.bands)]){
    let end=0;
    for(let index=0;index<bands.length;index++){
      const band=bands[index];
      if(!Number.isFinite(band.lowerExclusive) || band.lowerExclusive<0 || (band.upperInclusive!==null && (!Number.isFinite(band.upperInclusive) || band.upperInclusive<=band.lowerExclusive)) || (index>0 && band.lowerExclusive<end))return '区间须为左开右闭，不能重叠或倒置';
      end=band.upperInclusive ?? Infinity;
      for(const field of ['firstQuantity','firstFee','additionalQuantity','additionalFee'] as const){
        const value=band[field], quantity=field.endsWith('Quantity');
        if(value===null){if(enabled)return '启用前请补全全部费率';continue;}
        if(!Number.isFinite(value) || (quantity?value<=0:value<0))return quantity?'首量和续量必须大于零':'费用不得为负数';
        if(input.rules.mode==='piece' && quantity && !Number.isInteger(value))return '首件数和续件数必须为正整数';
      }
    }
    if(enabled && bands!==input.rules.defaultBands && !bands.length)return '启用前请补全地区费率';
  }
  return '';
}
