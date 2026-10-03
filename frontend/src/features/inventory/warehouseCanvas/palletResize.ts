import type { PlannerRect } from './warehousePlannerScene'

export type ResizeHandle = 'n' | 'ne' | 'e' | 'se' | 's' | 'sw' | 'w' | 'nw'

const MIN_WIDTH = 100 / 120
const MIN_HEIGHT = 100 / 80

function clamp(value: number, minimum: number, maximum: number) {
  return Math.max(minimum, Math.min(maximum, value))
}

export function resizePalletRect(
  origin: PlannerRect,
  handle: ResizeHandle,
  delta: { x: number; y: number },
  snap: boolean,
): PlannerRect {
  let left = origin.left
  let top = origin.top
  let right = origin.left + origin.width
  let bottom = origin.top + origin.height
  const movedEdge = (edge: number, movement: number, gridStep: number) => {
    const value = edge + movement
    return snap ? Math.round(value / gridStep) * gridStep : value
  }

  if (handle.includes('w')) left = clamp(movedEdge(left, delta.x, MIN_WIDTH), 0, right - MIN_WIDTH)
  if (handle.includes('e')) right = clamp(movedEdge(right, delta.x, MIN_WIDTH), left + MIN_WIDTH, 100)
  if (handle.includes('n')) top = clamp(movedEdge(top, delta.y, MIN_HEIGHT), 0, bottom - MIN_HEIGHT)
  if (handle.includes('s')) bottom = clamp(movedEdge(bottom, delta.y, MIN_HEIGHT), top + MIN_HEIGHT, 100)

  return { left, top, width: right - left, height: bottom - top }
}
