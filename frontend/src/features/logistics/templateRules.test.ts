import { describe, expect, it } from 'vitest';
import { newTemplate, validateTemplate, moveProvinces } from './templateRules';
describe('freight template editing rules',()=>{
  it('keeps empty fees as draft and requires complete numeric bands for enable',()=>{
    const input=newTemplate();input.name='合同';input.productCode='P';input.productName='普运';input.originProvince='浙江省';input.originCity='金华市';input.originCounty='义乌市';
    expect(input.rules.defaultBands[0].firstFee).toBeNull();
    expect(validateTemplate(input)).toBe('');
    input.status='enabled';expect(validateTemplate(input)).toContain('费率');
    Object.assign(input.rules.defaultBands[0],{firstQuantity:1,firstFee:0,additionalQuantity:1,additionalFee:0});
    expect(validateTemplate(input)).toBe('');
    input.rules.mode='piece';input.rules.defaultBands[0].additionalQuantity=0.5;
    expect(validateTemplate(input)).toContain('正整数');
  });
  it('moves a province only after caller confirmation and preserves other groups',()=>{
    const groups=[{name:'华东',provinces:['330000','310000'],bands:[]},{name:'单独合同',provinces:[],bands:[]}];
    const moved=moveProvinces(groups,1,['330000']);
    expect(moved[0].provinces).toEqual(['310000']);expect(moved[1].provinces).toEqual(['330000']);
    expect(groups[0].provinces).toContain('330000');
  });
  it('rejects duplicate region assignments and overlapping or inverted ranges',()=>{
    const input=newTemplate();input.name='合同';input.rules.regionGroups=[{name:'一',provinces:['330000'],bands:[]},{name:'二',provinces:['330000'],bands:[]}];
    expect(validateTemplate(input)).toContain('重复');
    input.rules.regionGroups=[];input.rules.defaultBands[0].upperInclusive=0;
    expect(validateTemplate(input)).toContain('区间');
  });
});
