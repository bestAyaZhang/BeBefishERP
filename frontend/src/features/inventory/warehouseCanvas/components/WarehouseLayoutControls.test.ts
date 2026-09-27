import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import WarehouseLayoutControls from './WarehouseLayoutControls.vue'
import { blankWarehouseLayout } from '../warehouseLayoutService'

describe('WarehouseLayoutControls', () => {
  it('loads the default warehouse and saves a revisioned snapshot', async () => {
    const document = blankWarehouseLayout()
    const service = {load:vi.fn().mockResolvedValue({revision:2,document}),save:vi.fn().mockResolvedValue({revision:3,document})}
    const wrapper = mount(WarehouseLayoutControls,{props:{document,busy:false},global:{provide:{masterdataService:{listWarehouses:vi.fn().mockResolvedValue({records:[{id:7,warehouseName:'正式仓库',defaultWarehouse:true}],total:1})},warehouseLayoutService:service}}})
    try {
      await flushPromises()
      expect(service.load).toHaveBeenCalledWith(7)
      expect(wrapper.emitted('loaded')?.[0]).toEqual([document])
      const edited = {...document,structure:{...document.structure,zones:[{id:'area',label:'A',tone:'blue' as const,left:10,top:10,width:10,height:10}]}}
      await wrapper.setProps({document:edited})
      await wrapper.get('button').trigger('click')
      await flushPromises()
      expect(service.save).toHaveBeenCalledWith(7,{revision:2,document:edited})
      expect(wrapper.text()).toContain('已保存')
    } finally { wrapper.unmount() }
  })
  it('does not claim a failed save succeeded or discard changes on a cancelled switch', async () => {
    const document = blankWarehouseLayout()
    const service = {load:vi.fn().mockResolvedValue({revision:1,document}),save:vi.fn().mockRejectedValue(new Error('规划已被其他人修改'))}
    const confirm = vi.spyOn(window,'confirm').mockReturnValue(false)
    const wrapper = mount(WarehouseLayoutControls,{props:{document,busy:false},global:{provide:{masterdataService:{listWarehouses:vi.fn().mockResolvedValue({records:[{id:1,warehouseName:'A'},{id:2,warehouseName:'B'}],total:2})},warehouseLayoutService:service}}})
    try {
      await flushPromises()
      await wrapper.setProps({document:{...document,structure:{...document.structure,zones:[{id:'area',label:'A',tone:'blue',left:10,top:10,width:10,height:10}]}}})
      await wrapper.get('button').trigger('click'); await flushPromises()
      expect(wrapper.get('[role="alert"]').text()).toContain('规划已被其他人修改')
      expect(wrapper.get('[role="status"]').text()).toBe('未保存')
      await wrapper.get('select').setValue('2'); await flushPromises()
      expect(service.load).toHaveBeenCalledTimes(1)
      expect(wrapper.get('select').element.value).toBe('1')
    } finally { wrapper.unmount(); confirm.mockRestore() }
  })
})
