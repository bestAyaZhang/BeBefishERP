import { describe, expect, it } from 'vitest';
import * as shipping from './types';

const valid=()=>{const form=shipping.emptyShipmentForm();Object.assign(form,{senderPhone:'123456',senderDetailAddress:'仓库',recipientPhone:'123456',recipientDetailAddress:'收件路'});Object.assign(form.orderDraft,{weight:1.25,volume:0.12,pieceAmount:1,yimi:{visitDelivery:0,deliveryFlag:null,floorNo:''}});return form;};
function validate(form:shipping.ShipmentFormInput,product='1'){
  expect(typeof shipping.validateYimiOrder).toBe('function');
  return shipping.validateYimiOrder(form,product);
}
describe('Yimi order-only constraints',()=>{
  it('accepts explicit pickup zero and one with normal door delivery and keeps old payment values',()=>{
    for(const visitDelivery of [0,1])for(const payType of [102,104,103]){
      const form=valid();form.orderDraft.yimi!.visitDelivery=visitDelivery;form.orderDraft.payType=payType;
      expect(validate(form)).toBe('');expect(form.orderDraft.payType).toBe(payType);
    }
  });
  it.each([
    ['missing pickup',(f:shipping.ShipmentFormInput)=>{f.orderDraft.yimi=undefined;},'揽货'],
    ['fractional quantity',(f:shipping.ShipmentFormInput)=>{f.orderDraft.pieceAmount=1.5;},'件数'],
    ['too many packages',(f:shipping.ShipmentFormInput)=>{f.orderDraft.pieceAmount=1001;},'1000'],
    ['weight precision',(f:shipping.ShipmentFormInput)=>{f.orderDraft.weight=1.001;},'两位小数'],
    ['volume precision',(f:shipping.ShipmentFormInput)=>{f.orderDraft.volume=0.001;},'两位小数'],
    ['long sender address',(f:shipping.ShipmentFormInput)=>{f.senderDetailAddress='地'.repeat(101);},'100'],
    ['long recipient address',(f:shipping.ShipmentFormInput)=>{f.recipientDetailAddress='地'.repeat(51);},'50'],
    ['long phone',(f:shipping.ShipmentFormInput)=>{f.recipientPhone='1'.repeat(21);},'20'],
    ['long remark',(f:shipping.ShipmentFormInput)=>{f.orderDraft.logisticsRemark='字'.repeat(101);},'100'],
    ['contradictory flag',(f:shipping.ShipmentFormInput)=>{f.orderDraft.yimi!.deliveryFlag=1;},'送货方式'],
    ['missing elevator',(f:shipping.ShipmentFormInput)=>{f.orderDraft.goodsType=179;},'电梯'],
  ])('rejects %s without normalizing away the invalid value',(_name,change,message)=>{
    const form=valid();change(form);const before=JSON.stringify(form);expect(validate(form)).toContain(message);expect(JSON.stringify(form)).toBe(before);
  });
  it('requires a matching service product and explicit upstairs elevator with allowed floor values',()=>{
    const form=valid();form.orderDraft.goodsType=285;form.orderDraft.yimi!.deliveryFlag=1;
    expect(validate(form,'2')).toBe('');expect(validate(form,'1')).toContain('港到港');
    form.orderDraft.goodsType=179;form.orderDraft.yimi!.deliveryFlag=3;
    for(const floor of ['', '-2','-1','2','3','4','5','6','7']){form.orderDraft.yimi!.floorNo=floor;expect(validate(form)).toBe('');}
    form.orderDraft.yimi!.floorNo='8';expect(validate(form)).toContain('楼层');
    form.orderDraft.yimi!.deliveryFlag=2;form.orderDraft.yimi!.floorNo='';expect(validate(form)).toBe('');
    expect(validate(form,'2')).toContain('港到门');
  });
});
