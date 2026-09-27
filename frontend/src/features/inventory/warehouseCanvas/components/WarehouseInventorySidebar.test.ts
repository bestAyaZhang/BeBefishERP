import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import WarehouseInventorySidebar from './WarehouseInventorySidebar.vue'
import { createMockWarehouseInventoryService } from '../warehouseInventoryService'
import { warehousePlannerScene } from '../warehousePlannerScene'

const palletGroups = ['pallet-a01', 'pallet-c018', 'pallet-a03'].map(id => (
  warehousePlannerScene.palletGroups.find(item => item.id === id)!
))

async function mountSidebar() {
  return mount(WarehouseInventorySidebar, {
    props: {
      inventory: await createMockWarehouseInventoryService().load(8),
      loading: false,
      error: '',
      selectedPalletId: null,
      palletGroups,
    },
  })
}

describe('WarehouseInventorySidebar', () => {
  it('lists every planned pile without the removed summary card', async () => {
    const wrapper = await mountSidebar()

    expect(wrapper.get('h2').text()).toBe('货物堆与实际库存')
    expect(wrapper.find('[aria-label="货物堆库存概览"]').exists()).toBe(false)
    expect(wrapper.findAll('[data-testid^="inventory-pile-pallet-"]')).toHaveLength(3)

    const emptyPile = wrapper.get('[data-testid="inventory-pile-pallet-a03"]')
    expect(emptyPile.text()).toContain('A03')
    expect(emptyPile.text()).toContain('0种 SKU · 0个')
    expect(emptyPile.text()).toContain('暂无实际库存')
  })

  it('aggregates real SKU quantities by pile and opens the selected pile', async () => {
    const wrapper = await mountSidebar()
    const pile = wrapper.get('[data-testid="inventory-pile-pallet-c018"]')

    expect(pile.text()).toContain('C-018')
    expect(pile.text()).toContain('3种 SKU · 250个')
    expect(pile.text()).toContain('深海矿物水 500ml 蓝 / SKU-FISH-500ML-蓝')
    expect(pile.text()).toContain('120个')
    expect(pile.text()).toContain('12oz 冷饮杯 / SKU-CUP-12OZ')
    expect(pile.text()).toContain('80个')

    await pile.trigger('click')
    expect(wrapper.emitted('select-pallet')?.[0]).toEqual(['pallet-c018'])
  })

  it('filters piles by pile identity or their actual SKU data', async () => {
    const wrapper = await mountSidebar()
    const search = wrapper.get('[data-testid="inventory-pile-search"]')

    await search.setValue('A03')
    expect(wrapper.find('[data-testid="inventory-pile-pallet-a03"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="inventory-pile-pallet-c018"]').exists()).toBe(false)

    await search.setValue('SKU-FISH-500ML-蓝')
    expect(wrapper.find('[data-testid="inventory-pile-pallet-a01"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="inventory-pile-pallet-c018"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="inventory-pile-pallet-a03"]').exists()).toBe(false)
  })
})
