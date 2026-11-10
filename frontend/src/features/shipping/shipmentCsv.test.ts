import { describe, expect, it } from 'vitest';
import { buildShipmentCsv } from './shipmentCsv';
import { emptyShipmentForm, type Shipment, type ShipmentContent } from './types';

function shipment(content: Partial<ShipmentContent> = {}, shipmentFields: Partial<Shipment> = {}): Shipment {
  return {
    id: 51, version: 3, shipmentNo: 'FH20261110-2026111000000051', logisticsOrderState: 'succeeded',
    createdBy: 'internal-creator', updatedBy: 'internal-updater', creatorEmployeeId: 12,
    createdAt: '2026-11-10T09:00:00', updatedAt: '2026-11-10T10:00:00',
    content: {
      ...emptyShipmentForm(), shipmentDate: '2026-11-10', status: 'unfinished', platform: '淘宝',
      shopName: '旗舰店', recipientName: '林女士', recipientPhone: '13800006028',
      recipientProvince: '浙江省', recipientCity: '杭州市', recipientCounty: '余杭区', recipientDetailAddress: '示例路18号',
      senderName: '内部寄件人', senderPhone: '13900001111', senderDetailAddress: '内部仓库地址',
      preparationContent: '玻璃杯 × 2', remark: '外箱加固', logisticsCompany: '安能物流', trackingNo: 'ANE001',
      preparers: ['小周', '阿杰'], orderer: '李主管', ...content
    },
    ...shipmentFields
  };
}

// These fixtures have one record without commas or newlines in its cells.
function plainRow(csv: string): Record<string, string> {
  const [header, row] = csv.slice(1).split('\r\n');
  return Object.fromEntries(header.split(',').map((key, index) => [key, row.split(',')[index]]));
}

describe('shipment CSV', () => {
  it('writes a UTF-8 BOM and preserves commas, quotes and line breaks in text', () => {
    const csv = buildShipmentCsv([shipment({ preparationContent: '玻璃杯, "加厚"\r\n滤材 × 6', remark: '复核后\n封箱' })]);

    expect(csv).toMatch(/^\uFEFF发货单号,/u);
    expect(csv).toContain('"玻璃杯, ""加厚""\r\n滤材 × 6","复核后\n封箱"');
    expect(csv.endsWith('\r\n')).toBe(true);
  });

  it.each(['=1+1', '+SUM(A1)', '-10+20', '@SUM(A1)', ' \t=1+1'])('neutralizes formula-like cells: %j', (value) => {
    const row = plainRow(buildShipmentCsv([shipment({ recipientName: value, remark: value })]));

    expect(row['收件人']).toBe(`'${value}`);
    expect(row['备注']).toBe(`'${value}`);
  });

  it('leaves missing cells blank while preserving numeric zero', () => {
    const row = plainRow(buildShipmentCsv([shipment({
      remark: '   ', estimatedFreight: 0, orderDraft: { ...emptyShipmentForm().orderDraft, weight: 0 }
    })]));

    expect(row).toMatchObject({ '备注': '', '实际重量(kg)': '', '下单重量(kg)': '0', '运费预测(元)': '0' });
  });

  it('shows a cancelled waybill as void without disclosing its old number', () => {
    const csv = buildShipmentCsv([shipment({ trackingNo: 'OLD-ANE-001' }, { logisticsOrderState: 'cancelled' })]);

    expect(plainRow(csv)).toMatchObject({ '物流状态': '已取消', '运单号': '原运单已作废' });
    expect(csv).not.toContain('OLD-ANE-001');
  });

  it.each(['+86 138-0000-6028', '0571-12345678', '1234'])('masks nonstandard contact formats: %j', (recipientPhone) => {
    const csv = buildShipmentCsv([shipment({ recipientPhone })]);

    expect(plainRow(csv)['收件电话']).toBe('****');
    expect(csv).not.toContain(recipientPhone);
  });

  it('exports only the list columns and keeps the full shipment number and masked mobile', () => {
    const record = shipment({}, { preparation: { actualWeight: 18.125, updatedBy: 'internal-weigher', updatedAt: null } });
    const csv = buildShipmentCsv([record]);

    expect(plainRow(csv)).toEqual({
      发货单号: record.shipmentNo, 发货日期: '2026-11-10', 平台: '淘宝', 店铺: '旗舰店',
      收件人: '林女士', 收件电话: '138****6028', 收件地址: '浙江省杭州市余杭区示例路18号',
      备货清单: '玻璃杯 × 2', 备注: '外箱加固', 物流公司: '安能物流', 物流状态: '下单成功', 运单号: 'ANE001',
      '实际重量(kg)': '18.125', '下单重量(kg)': '', '运费预测(元)': '', 备货状态: '未完成', 备货人: '小周、阿杰', 下单人: '李主管'
    });
    expect(csv).not.toMatch(/13800006028|13900001111|内部仓库地址|internal-/u);
  });
});
