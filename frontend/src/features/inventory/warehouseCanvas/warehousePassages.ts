import type { PlannerZone, PlannerZoneKind } from './warehousePlannerScene'

export const zoneKinds: { id: PlannerZoneKind; label: string }[] = [
  { id: 'area', label: '普通区域' }, { id: 'aisle', label: '普通通道' },
  { id: 'forklift', label: '叉车通道' }, { id: 'fire', label: '消防留空区' },
]
export function isPassage(zone: Pick<PlannerZone, 'kind'>): boolean {
  return zone.kind === 'aisle' || zone.kind === 'forklift' || zone.kind === 'fire'
}
export function passageAxis(zone: PlannerZone): 'horizontal' | 'vertical' {
  return zone.axis ?? (zone.width * .6 >= zone.height * .4 ? 'horizontal' : 'vertical')
}
export function passageWidthMeters(zone: PlannerZone): number {
  return passageAxis(zone) === 'horizontal' ? zone.height * .4 : zone.width * .6
}
