import { afterEach, expect, it, vi } from 'vitest';
afterEach(() => { vi.unstubAllEnvs(); vi.resetModules(); });
it('uses real organization and sales services in production', async () => {
 vi.stubEnv('PROD', true); vi.stubEnv('VITE_DATA_SOURCE', 'real'); vi.resetModules();
 const { organizationService } = await import('../features/organization/organizationService');
 const { httpOrganizationService } = await import('../features/organization/httpOrganizationService');
 expect(organizationService).toBe(httpOrganizationService);
 const { salesOrderService } = await import('../features/sales/salesOrderService');
 const { httpSalesOrderService } = await import('../features/sales/httpSalesOrderService');
 expect(salesOrderService).toBe(httpSalesOrderService);
 const { regionService } = await import('../features/masterdata/regionService');
 await expect(regionService.listProvinces()).rejects.toThrow('地区');
});
it.each([() => import('../features/organization/organizationService'), () => import('../features/masterdata/regionService'), () => import('../features/sales/salesOrderService')])('rejects production mock configuration for %s', async (path) => {
 vi.stubEnv('PROD', true); vi.stubEnv('VITE_DATA_SOURCE', 'mock'); vi.resetModules();
 await expect(path()).rejects.toThrow('生产构建不允许');
});
