export type PlannerRect = {
  left: number
  top: number
  width: number
  height: number
}

export type PlannerDoor = PlannerRect & { id: string; label: string; widthMeters: number }
export type PlannerZone = PlannerRect & { id: string; label: string; tone: 'green' | 'blue' }
export type PlannerAisle = PlannerRect & { id: string; label: string; direction: 'horizontal' | 'vertical' }
export type PlannerRoom = PlannerRect & { id: string; label: string; detail: string }
export type PlannerFireLane = PlannerRect & { id: string; rotation: number }
export type PlannerColumn = { id: string; left: number; top: number }
export type PlannerPalletContent = {
  productName: string
  skuCode: string
  units: number
  unitsPerCase: number
}

export type PlannerPalletGroup = PlannerRect & {
  id: string
  code: string
  name: string
  columns: number
  rows: number
  xMeters: number
  yMeters: number
  lengthMeters: number
  widthMeters: number
  rotation: number
  contents: PlannerPalletContent[]
}

const pallet = (
  id: string,
  left: number,
  top: number,
  width: number,
  height: number,
  columns: number,
  rows: number,
  overrides: Partial<PlannerPalletGroup> = {},
): PlannerPalletGroup => ({
  id,
  code: id.replace('pallet-', '').toUpperCase(),
  name: `地面货堆 ${id.replace('pallet-', '').toUpperCase()}`,
  left,
  top,
  width,
  height,
  columns,
  rows,
  xMeters: Number((left * .64).toFixed(1)),
  yMeters: Number((top * .42).toFixed(1)),
  lengthMeters: Number((width * .54).toFixed(1)),
  widthMeters: Number((height * .42).toFixed(1)),
  rotation: 0,
  contents: [{
    productName: `仓内商品 ${id.replace('pallet-', '').toUpperCase()}`,
    skuCode: `SKU-${id.replace('pallet-', '').toUpperCase()}`,
    units: 144,
    unitsPerCase: 24,
  }],
  ...overrides,
})

export const warehousePlannerScene = {
  doors: [
    { id: 'door-1', label: '1号装卸门', widthMeters: 4, left: 19, top: 4, width: 8, height: 7 },
    { id: 'door-2', label: '2号装卸门', widthMeters: 4, left: 47, top: 4, width: 8, height: 7 },
    { id: 'door-3', label: '3号装卸门', widthMeters: 4, left: 75, top: 4, width: 8, height: 7 },
  ] satisfies PlannerDoor[],
  zones: [
    { id: 'zone-receiving', label: '收货区', tone: 'green', left: 10, top: 13, width: 25, height: 15 },
    { id: 'zone-buffer', label: '暂存区', tone: 'blue', left: 40, top: 13, width: 23, height: 15 },
    { id: 'zone-shipping', label: '发货区', tone: 'green', left: 68, top: 13, width: 25, height: 15 },
  ] satisfies PlannerZone[],
  aisles: [
    { id: 'aisle-top', label: '叉车通道 4.0m', direction: 'horizontal', left: 11, top: 30, width: 81, height: 5 },
    { id: 'aisle-west', label: '叉车通道 4.0m', direction: 'vertical', left: 25, top: 38, width: 5, height: 43 },
    { id: 'aisle-center', label: '叉车通道 4.0m', direction: 'vertical', left: 48, top: 38, width: 5, height: 43 },
    { id: 'aisle-east', label: '叉车通道 4.0m', direction: 'vertical', left: 76, top: 38, width: 5, height: 43 },
  ] satisfies PlannerAisle[],
  rooms: [
    { id: 'room-equipment', label: '设备间', detail: '6.0 × 4.0m', left: 6, top: 82, width: 13, height: 10 },
    { id: 'room-office', label: '办公区', detail: '8.0 × 4.0m', left: 19, top: 82, width: 17, height: 10 },
  ] satisfies PlannerRoom[],
  fireLanes: [
    { id: 'fire-upper', left: 92.5, top: 12, width: 3, height: 25, rotation: -13 },
    { id: 'fire-lower', left: 91.5, top: 64, width: 3, height: 27, rotation: 13 },
  ] satisfies PlannerFireLane[],
  columns: [
    [17,37],[36,37],[55,37],[84,37],[17,60],[36,60],[55,60],[72,60],[86,60],
  ].map(([left, top], index) => ({ id: `column-${index + 1}`, left, top })) satisfies PlannerColumn[],
  palletGroups: [
    pallet('pallet-a01', 10.5, 38, 6, 7, 3, 2),
    pallet('pallet-a02', 18.5, 38, 6, 7, 3, 2),
    pallet('pallet-a03', 32, 38, 5, 7, 2, 3),
    pallet('pallet-a04', 39, 38, 8, 7, 4, 2),
    pallet('pallet-a05', 10.5, 48, 5.5, 6.5, 2, 3),
    pallet('pallet-a06', 18.5, 48, 6, 6.5, 3, 2),
    pallet('pallet-a07', 32, 48, 5, 6.5, 2, 3),
    pallet('pallet-a08', 39, 48, 8, 6.5, 4, 2),
    pallet('pallet-a09', 10.5, 58.5, 6, 6, 3, 2),
    pallet('pallet-a10', 18.5, 58.5, 6, 6, 3, 2),
    pallet('pallet-b01', 56, 38, 6.5, 6, 3, 2),
    pallet('pallet-b02', 64, 38, 9, 6, 4, 2),
    pallet('pallet-c018', 58.5, 49, 7, 5.5, 3, 2, {
      code: 'C-018',
      name: '地面货堆 C-018',
      xMeters: 32.4,
      yMeters: 21.8,
      lengthMeters: 4.8,
      widthMeters: 2.4,
      rotation: 90,
      contents: [
        { productName: '深海矿物水 500ml 蓝', skuCode: 'SKU-FISH-500ML-蓝', units: 120, unitsPerCase: 24 },
        { productName: '12oz 冷饮杯', skuCode: 'SKU-CUP-12OZ', units: 80, unitsPerCase: 50 },
        { productName: '茉莉绿茶 1L', skuCode: 'SKU-TEA-1L-绿', units: 50, unitsPerCase: 20 },
      ],
    }),
    pallet('pallet-b04', 56, 58, 14, 6, 7, 2),
    pallet('pallet-b05', 71.5, 58, 5, 6, 2, 3),
    pallet('pallet-c01', 82, 38, 11, 6, 5, 2),
    pallet('pallet-c02', 82, 48, 11, 6, 5, 2),
    pallet('pallet-c03', 82, 58, 11, 6, 5, 2),
    pallet('pallet-a11', 10.5, 68, 6, 6, 3, 2),
    pallet('pallet-a12', 18.5, 68, 6, 6, 3, 2),
    pallet('pallet-b06', 32, 68, 7, 6, 3, 2),
    pallet('pallet-b07', 40, 68, 7, 6, 3, 2),
    pallet('pallet-b08', 56, 68, 10, 6, 5, 2),
    pallet('pallet-b09', 67.5, 68, 9, 6, 4, 2),
    pallet('pallet-c04', 82, 68, 11, 6, 5, 2),
    pallet('pallet-b10', 40, 76, 8, 5, 4, 2),
    pallet('pallet-b11', 56, 76, 10, 5, 5, 2),
    pallet('pallet-c05', 70, 76, 8, 5, 4, 2),
    pallet('pallet-c06', 83, 76, 9, 5, 4, 2),
  ] satisfies PlannerPalletGroup[],
} as const
