import { describe, expect, it } from 'vitest'
import { resizePalletRect } from './palletResize'
import type { ResizeHandle } from './palletResize'

const origin = { left: 30, top: 25, width: 20, height: 15 }

describe('resizePalletRect', () => {
  it.each<ResizeHandle>(['n', 'ne', 'e', 'se', 's', 'sw', 'w', 'nw'])(
    'moves only the %s handle edges while retaining the opposite edges', handle => {
      const result = resizePalletRect(origin, handle, { x: 5, y: 4 }, false)
      const west = handle.includes('w'), east = handle.includes('e')
      const north = handle.includes('n'), south = handle.includes('s')
      expect(result.left).toBe(west ? 35 : 30)
      expect(result.top).toBe(north ? 29 : 25)
      expect(result.left + result.width).toBe(east ? 55 : 50)
      expect(result.top + result.height).toBe(south ? 44 : 40)
    },
  )

  it('clamps at one creation cell and the board edges without flipping the rectangle', () => {
    const west = resizePalletRect(origin, 'w', { x: 100, y: 0 }, false)
    expect(west.left + west.width).toBe(50)
    expect(west.width).toBeCloseTo(100 / 120)
    const north = resizePalletRect(origin, 'n', { x: 0, y: 100 }, false)
    expect(north.top + north.height).toBe(40)
    expect(north.height).toBeCloseTo(100 / 80)
    expect(resizePalletRect(origin, 'nw', { x: -100, y: -100 }, false)).toMatchObject({ left: 0, top: 0 })
    const southeast = resizePalletRect(origin, 'se', { x: 100, y: 100 }, false)
    expect(southeast.left + southeast.width).toBe(100)
    expect(southeast.top + southeast.height).toBe(100)
  })

  it('snaps only the moving edges when snapping is enabled', () => {
    const loose = resizePalletRect(origin, 'se', { x: 2.1, y: 2.1 }, false)
    const snapped = resizePalletRect(origin, 'se', { x: 2.1, y: 2.1 }, true)
    expect(loose).toEqual({ left: 30, top: 25, width: 22.1, height: 17.1 })
    expect(snapped.left).toBe(origin.left)
    expect(snapped.top).toBe(origin.top)
    expect(snapped.left + snapped.width).toBeCloseTo(Math.round(52.1 / (100 / 120)) * (100 / 120))
    expect(snapped.top + snapped.height).toBeCloseTo(Math.round(42.1 / (100 / 80)) * (100 / 80))
  })
})
